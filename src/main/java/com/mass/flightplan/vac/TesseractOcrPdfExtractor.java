package com.mass.flightplan.vac;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.global.lept;
import org.bytedeco.tesseract.TessBaseAPI;
import org.bytedeco.tesseract.global.tesseract;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static java.lang.Math.min;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static org.bytedeco.leptonica.global.lept.pixDestroy;
import static org.bytedeco.tesseract.global.tesseract.RIL_BLOCK;

@RequiredArgsConstructor
@Log4j2
class TesseractOcrPdfExtractor
    implements VacPdfExtractor
{
    private final @NonNull ThreadPoolTaskExecutor executor;
    private final @NonNull Duration timeout;
    private final boolean cleanUp;
    private final int ocrDpi;


    private Path storeOutput(Collection<? extends OcrBlock> output)
        throws IOException
    {
        Path p = Files.createTempFile("ocr_output", ".txt");
        try (var w = Files.newBufferedWriter(p)) {
            List<String> lines =
                output.stream()
                      .sorted(OcrBlock.TOP_FIRST_THEN_LEFT)
                      .map(b -> String.format("%03d::[%4f, %4f] -> [%4f, %4f]%n%s", b.page(), b.top(), b.left(), b.bottom(), b.right(), b.text()))
                      .toList();

            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        }

        return p;
    }

    @Override
    public @NonNull Collection<? extends OcrBlock> extractPdfText(@NonNull Resource pdf)
        throws IOException, InterruptedException
    {
        Instant start = now();

        var future = executor
            .submitCompletable(() -> convertPdfToImages(pdf, timeout))
            .thenCompose(files -> executor.submitCompletable(() -> performImageOcr(files, pdf.getDescription(), timeout)));

        try {
            Collection<? extends OcrBlock> ret = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            log.info("OCR extraction for {} complete after {}ms", pdf, between(start, now()).toMillis());

            if (!cleanUp) {
                Path p = storeOutput(ret);
                log.debug("Stored OCR output under: {}", p);
            }

            return ret;
        } catch (TimeoutException tex) {
            future.cancel(true);
            throw new IOException("OCR extraction process timed out after " + timeout);
        } catch (ExecutionException | CompletionException eex) {
            if (eex.getCause() instanceof IOException) {
                throw (IOException) eex.getCause();
            }
            else {
                throw new IOException("OCR extraction failed for " + pdf.getDescription(), eex.getCause());
            }
        }
    }

    private @NonNull List<ByteBuffer> convertPdfToImages(@NonNull Resource pdf, @NonNull Duration timeout)
        throws IOException, InterruptedException, CompletionException
    {
        Instant start = now();

        PDDocument doc;
        try (var in = pdf.getInputStream()) {
            doc = PDDocument.load(in);
        }

        CompletableFuture<ByteBuffer>[] cfStash = new CompletableFuture[doc.getNumberOfPages()];

        log.debug("Converting {} PDF pages to grayscale images with {} DPI", doc.getNumberOfPages(), ocrDpi);
        for (int ii = 0; ii < doc.getNumberOfPages(); ii++) {
            final int page = ii;
            cfStash[page] = executor.submitCompletable(() -> {
                try {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    BufferedImage img = renderer.renderImage(page, ocrDpi / 72f, ImageType.GRAY);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "tif", baos);
                    return ByteBuffer.wrap(baos.toByteArray());
                } catch (Exception x) {
                    throw new IOException("Failed to convert page " + page + " on " + pdf.getDescription() + " to image", x);
                }
            });
        }

        CompletableFuture.allOf(cfStash).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();

        List<ByteBuffer> ret = new ArrayList<>();

        for (var cf : cfStash) {
            try {
                ret.add(cf.get());
            } catch (ExecutionException eex) {
                throw new AssertionError();
            }
        }

        log.debug("Finished converting {} to {} images in {}ms", pdf, doc.getNumberOfPages(), between(start, now()).toMillis());

        return ret;
    }

    private @NonNull Collection<? extends OcrBlock> performImageOcr(
        @NonNull Collection<ByteBuffer> images, String documentName, @NonNull Duration timeout
    )
    {
        @SuppressWarnings("unchecked")
        CompletableFuture<Collection<? extends OcrBlock>>[] stash = new CompletableFuture[images.size()];
        int pageNum = 0;
        for (var image : images) {
            final int index = pageNum;
            stash[index] = executor.submitCompletable(() -> performImageOcr(image, documentName, index));
            pageNum++;
        }

        CompletableFuture.allOf(stash).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join(); //throws

        var ret = Arrays.stream(stash)
                        .map(cf -> {
                            try {
                                return cf.get();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                            return Set.<OcrBlock>of();
                        })
                        .flatMap(Collection::stream)
                        .toList();

        log.debug("Extracted {} blocks from {} pages", ret.size(), images.size());

        return ret;
    }

    private @NonNull Collection<? extends OcrBlock> performImageOcr(@NonNull ByteBuffer imageData, String documentName, int pageNum)
        throws IOException
    {
        Collection<OcrBlock> ret = new ArrayList<>();

        TessBaseAPI tessApi = new TessBaseAPI();

        int code;
        if (0 != (code = tessApi.Init(null, "eng+fra"))) {
            throw new IllegalStateException("Could not initialise tesseract API: " + code);
        }

        try {
            tessApi.SetPageSegMode(tesseract.PSM_SPARSE_TEXT);
            tessApi.SetVariable("preserve_interword_spaces", "true");
            tessApi.SetVariable("textord_space_size_is_variable", "true");

            final int[] left = new int[1], top = new int[1], bottom = new int[1], right = new int[1];
            PIX image = lept.pixReadMem(imageData, imageData.remaining());
            tessApi.SetImage(image);

            double imageHeight = tessApi.GetInputImage().h();
            double imageWidth = tessApi.GetInputImage().w();

            tessApi.SetSourceResolution(ocrDpi);

            final int recog = tessApi.Recognize(null);
            int pageBlockCount = 0;

            var it = tessApi.GetIterator();
            it.Begin();
            while (it.Next(RIL_BLOCK)) {
                it.BoundingBox(RIL_BLOCK, 5, left, top, right, bottom);
                var txt = it.GetUTF8Text(RIL_BLOCK);
                ret.add(
                    OcrBlock.builder()
                            .page(pageNum).text(txt.getString().trim()) //NOTE the trim()
                            .left(left[0] / imageWidth).right(right[0] / imageWidth)
                            .top(top[0] / imageHeight).bottom(bottom[0] / imageHeight)
                            .build()
                );
                pageBlockCount++;
//                    log.debug("Got BLOCK at ({}, {}) -> ({}, {}):%n{}", top[0], left[0], bottom[0], right[0], txt.getString());
            }

            it.deallocate();
            pixDestroy(image);

            log.debug("Extracted {} OCR blocks from page {}", pageBlockCount, pageNum);
        } catch (Exception x) {
            throw new IOException("Failed to perform OCR on page " + pageNum + " of " + documentName, x);
        } finally {
            tessApi.End();
        }

        return mergeBlocksOnSameLine(ret);
    }

    /*
        Can't find a way to do this in Tesseract... which there ought to be.
     */
    private Collection<? extends OcrBlock> mergeBlocksOnSameLine(Collection<? extends OcrBlock> blocks){
        if(blocks.size() < 2){
            return blocks;
        }

        List<OcrBlock> list = new ArrayList<>(blocks);
        list.sort(OcrBlock.TOP_FIRST_THEN_LEFT);

        for(int prev = 0, curr = 1; curr < list.size(); curr++){
            OcrBlock pb = list.get(prev), cb = list.get(curr);
            if(pb.vDist(cb) < -MIN_VOVERLAP_REQUIRED_FOR_MERGE && pb.hDist(cb) < MAX_HDIST_REQUIRED_FOR_MERGE){
                list.set(prev, merge(pb, cb));
                list.set(curr, null);
            } else {
                prev = curr;
            }
        }

        return list.stream().filter(Objects::nonNull).toList();
    }

    private static final double MIN_VOVERLAP_REQUIRED_FOR_MERGE = .75;
    private static final double MAX_HDIST_REQUIRED_FOR_MERGE = .05;

    private static OcrBlock merge(OcrBlock b1, OcrBlock b2){
        return OcrBlock.builder()
            .page(b1.page())
            .top(min(b1.top(), b2.top()))
            .left(min(b1.left(), b2.left()))
            .bottom(min(b1.bottom(), b2.bottom()))
            .right(min(b1.right(), b2.right()))
            .text(b1.left() < b2.left() ? b1.text() + "\t" + b2.text() : b2.text() + "\t" + b1.text())
       .build();
    }

}
