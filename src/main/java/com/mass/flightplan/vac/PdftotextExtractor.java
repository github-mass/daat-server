package com.mass.flightplan.vac;

import com.mass.flightplan.util.InvalidXmlCharFilterReader;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

@Log4j2
@RequiredArgsConstructor
public class PdftotextExtractor
    implements VacPdfExtractor
{
    private final @NonNull ThreadPoolTaskExecutor executor;
    private final @NonNull Duration timeout;

    @Override
    public @NonNull Result extractPdfText(@NonNull Resource pdf)
        throws IOException, InterruptedException
    {
        try {
            var bblockXml = invokePdfToText(pdf, executor, timeout);
            var bbox = extractFromBboxXml(bblockXml);
            return buildResult(bbox);
        }
        catch (XMLStreamException sex) {
            throw new IOException("Could not parse pdftotext bbox output", sex);
        }
    }

    private static Result buildResult(BBox bbox) {
        List<TextBlock> mergedInFlow = new ArrayList<>();
        List<TextBlock> mergedInPage = new ArrayList<>();

        for (Collection<? extends TextBlock> blocks : bbox.linesInFlow()) {
            mergeNeighbouringBlocks(blocks).forEach(mergedInFlow::add);
        }

        mergeNeighbouringBlocks(mergedInFlow).forEach(mergedInPage::add);

        return Result.builder()
                     .words(List.copyOf(bbox.words()))
                     .blocks(List.copyOf(bbox.lines()))
                     .blocksMergedInFlow(List.copyOf(mergedInFlow))
                     .blocksMergedInPage(List.copyOf(mergedInPage))
                     .build()
            ;
    }

    private static Stream<TextBlock> mergeNeighbouringBlocks(Collection<? extends TextBlock> blocks) {
        List<TextBlock> tmp = new ArrayList<>(blocks);
        tmp.sort(TextBlock.Comp.TOP_FIRST_THEN_LEFT);

        for (int prev = 0, curr = 1; curr < tmp.size(); curr++) {
            TextBlock pb = tmp.get(prev), cb = tmp.get(curr);
            if (pb.vRelDist(cb) <= -MIN_VOVERLAP_REQUIRED_FOR_MERGE && pb.hAbsDist(cb) <= MAX_ABS_H_DIST_FOR_MERGE) {
                tmp.set(prev, TextBlock.merge(pb, cb, " "));
                tmp.set(curr, null);
            }
            else {
                prev = curr;
            }
        }

        return tmp.stream().filter(Objects::nonNull);
    }

    private static final double MIN_VOVERLAP_REQUIRED_FOR_MERGE = .70;
    private static final double MAX_ABS_H_DIST_FOR_MERGE = 0.045; // 0.675cm on 15cm page

    private record BBox(Collection<TextBlock> words, Collection<TextBlock> lines,
                        Collection<Collection<TextBlock>> linesInFlow)
    {
    }

    private BBox extractFromBboxXml(CharSequence cs)
        throws XMLStreamException
    {
        XMLInputFactory fact = XMLInputFactory.newFactory();
        XMLStreamReader r = fact.createXMLStreamReader(new InvalidXmlCharFilterReader(new StringReader(cs.toString())));

        List<Collection<TextBlock>> flows = new ArrayList<>();
        List<TextBlock> words = new ArrayList<>();

        List<TextBlock> lines = new ArrayList<>();
        List<TextBlock> flowLines = new ArrayList<>();
        TextBlock.TextBlockBuilder current = null;
        final StringBuilder lineBuf = new StringBuilder();
        int page = -1;
        Double pageWidth = null, pageHeight = null;

        while (r.hasNext()) {
            r.next();

            /*
                Separate "flows" and don't merge from different "flows".
             */
            if (r.isStartElement() && "flow".equals(r.getLocalName())) {
                flowLines.clear();
            }
            else if (r.isEndElement() && "flow".equals(r.getLocalName())) {
                flows.add(List.copyOf(flowLines));
            }
            else if (r.isStartElement() && "page".equals(r.getLocalName())) {
                page++;
                pageWidth = Double.parseDouble(r.getAttributeValue(null, "width"));
                pageHeight = Double.parseDouble(r.getAttributeValue(null, "height"));
            }
            else if (r.isStartElement() && "word".equals(r.getLocalName())) {

                assert pageWidth != null;
                double xMin = Double.parseDouble(r.getAttributeValue(null, "xMin"));
                double xMax = Double.parseDouble(r.getAttributeValue(null, "xMax"));
                double yMin = Double.parseDouble(r.getAttributeValue(null, "yMin"));
                double yMax = Double.parseDouble(r.getAttributeValue(null, "yMax"));

                if (!lineBuf.isEmpty()) lineBuf.append(" ");
                // Must do this after having extracted attributes.
                String word = r.getElementText();
                lineBuf.append(word);

                words.add(
                    TextBlock.builder()
                             .page(page)
                             .left(xMin / pageWidth)
                             .right(xMax / pageWidth)
                             .top(yMin / pageHeight)
                             .bottom(yMax / pageHeight)
                             .text(word)
                             .build()
                );
            }
            else if (r.isStartElement() && "line".equals(r.getLocalName())) {
                assert pageWidth != null;

                current = TextBlock.builder();
                double xMin = Double.parseDouble(r.getAttributeValue(null, "xMin"));
                double xMax = Double.parseDouble(r.getAttributeValue(null, "xMax"));
                double yMin = Double.parseDouble(r.getAttributeValue(null, "yMin"));
                double yMax = Double.parseDouble(r.getAttributeValue(null, "yMax"));
                current.page(page)
                       .left(xMin / pageWidth)
                       .right(xMax / pageWidth)
                       .top(yMin / pageHeight)
                       .bottom(yMax / pageHeight);

                lineBuf.setLength(0);
            }
            else if (r.isEndElement() && "line".equals(r.getLocalName())) {
                assert current != null;

                current.text(lineBuf.toString());
                var block = current.build();
                flowLines.add(block);
                lines.add(block);
            }
        }

        r.close();

        return new BBox(words, lines, flows);
    }

    private static final String LINE_SEP = System.getProperty("line.separator");

    protected CharSequence invokePdfToText(Resource pdf, ThreadPoolTaskExecutor executor, Duration timeout)
        throws IOException, InterruptedException
    {
        // UTF-8 is default encoding but I want to keep a reminder
        List<String> cmd = List.of("pdftotext", "-bbox-layout", "-", "-");

        log.debug("Starting pdftotext process for {} ", pdf);
        Process p = createProcess(cmd);

        Future<Void> feeder = executor.submit(() -> {
            OutputStream processInput = p.getOutputStream();
            try (InputStream is = pdf.getInputStream()) {
                IOUtils.copy(is, processInput);
            }

            processInput.close();
            return null;
        });

        Future<CharSequence> collector = executor.submit(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; null != (line = br.readLine()); ) {
                    sb.append(line);
                    sb.append(LINE_SEP);
                }
            }

            return sb;
        });

        try {
            CharSequence cs = collector.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (p.exitValue() != 0) {
                throw new IOException("pdftotext process failed with code " + p.exitValue());
            }

            return cs;
        }
        catch (InterruptedException iex) {
            p.destroy();
            throw iex;
        }
        catch (TimeoutException tex) {
            p.destroy();
            throw new IOException("pdftotext process timed out after " + timeout);
        }
        catch (ExecutionException eex) {
            throw new IOException("pdftotext conversion failed", eex.getCause());
        }
    }

    protected Process createProcess(List<String> cmd)
        throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(cmd);

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        return pb.start();
    }

    public static void main(String[] args) {
        double h = 595.276000;
        double w = 419.528000;
        var b1 = TextBlock.builder().page(1).top(57.302103 / h).left(285.735952 / w).right(296.882192 / w).bottom(67.652183 / h).text("").build();
        var b2 = TextBlock.builder().page(1).top(57.302103 / h).left(305.958416 / w).right(307.773661 / w).bottom(67.652183 / h).text("").build();

        System.out.println("vDist: " + b1.vRelDist(b2));
        System.out.println("hDist: " + b1.hRelDist(b2));
    }
}
