package com.mass.flightplan.util;

import com.ximpleware.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class XPathDocumentExtractor {

    public static Builder from(@NonNull InputStream is)
        throws IOException
    {
        byte[] data = StreamUtils.copyToByteArray(is);
        return new Builder(data);
    }

    public static Builder from(@NonNull Resource resource)
        throws IOException
    {
        if (resource instanceof ByteArrayResource) {
            //avoid copy at the risk of sharing
            log.atWarn().log("Loading resource {} directly, subsequent modification before instance is built might corrupt process", resource);
            return new Builder(((ByteArrayResource) resource).getByteArray());
        }
        else {
            return new Builder(resource.getContentAsByteArray());
        }
    }

    public static Builder from(@NonNull Path path)
        throws IOException
    {
        byte[] data = Files.readAllBytes(path);
        return new Builder(data);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    public static class Builder {

        @Getter(AccessLevel.NONE)
        private final byte[] documentData;

        private boolean namespaceAware;

        public final XPathDocumentExtractor build()
            throws IOException
        {
            try {
                VTDGen gen = new VTDGen();
                gen.setDoc(documentData);
                gen.parse(namespaceAware);

                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();

                return new XPathDocumentExtractor(documentData, gen.getNav(), db);
            }
            catch (Exception x) {
                throw new IOException("Failed to build VTD tree", x);
            }
        }
    }

    private final byte[] documentData;
    @Getter
    private final VTDNav vtdNav;
    private final DocumentBuilder docBuilder;


    @NonNull
    public List<Node> extractNodes(@NonNull @Language("XPath") String expression)
        throws XPathExpressionException
    {
        try {
            Node fragment = docBuilder.newDocument().createDocumentFragment();

            AutoPilot ap = new AutoPilot(vtdNav);
            ap.selectXPath(expression);

            while (ap.evalXPath() != -1) {
                long offAndLen = vtdNav.getElementFragment();
                int off = (int) (offAndLen & 0xffffffff);
                int len = (int) (offAndLen >> 32);
                try (InputStream is = new ByteArrayInputStream(documentData, off, len)) {
                    InputSource input = new InputSource(is);
                    input.setEncoding(docEncoding(vtdNav));
                    Node n = fragment.getOwnerDocument().importNode(docBuilder.parse(input).getDocumentElement(), true);
                    fragment.appendChild(n);
                }
                catch (SAXException saex) {
                    throw new RuntimeException("Error trying to read XML chunk at: off=%d, len=%d".formatted(off, len), saex);
                }
            }

            NodeList nl = fragment.getChildNodes();
            return IntStream.range(0, nl.getLength()).mapToObj(nl::item).toList();
        }
        catch (XPathParseException xpex) {
            throw new XPathExpressionException(
                String.format("Invalid XPath expression '%s': %s", expression, xpex.getMessage())
            );
        }
        catch (VTDException | IOException other) {
            throw new RuntimeException("Unexpected error processing XML", other);
        }
    }

    @Nullable
    public Node extractNode(@NonNull @Language("XPath") String expression)
        throws XPathExpressionException
    {
        return extractNodes(expression).stream().findAny().orElse(null);
    }

    private static String docEncoding(VTDNav nav) {
        return switch (nav.getEncoding()) {
            case VTDNav.FORMAT_ASCII -> StandardCharsets.US_ASCII.name();
            case VTDNav.FORMAT_ISO_8859_1 -> StandardCharsets.ISO_8859_1.name();
            case VTDNav.FORMAT_UTF8 -> StandardCharsets.UTF_8.name();
            case VTDNav.FORMAT_UTF_16BE -> StandardCharsets.UTF_16BE.name();
            case VTDNav.FORMAT_UTF_16LE -> StandardCharsets.UTF_16LE.name();
            case VTDNav.FORMAT_ISO_8859_2 -> "ISO-8859-2";
            case VTDNav.FORMAT_ISO_8859_3 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-3");
            case VTDNav.FORMAT_ISO_8859_4 -> "ISO-8859-4";
            case VTDNav.FORMAT_ISO_8859_5 -> "ISO-8859-5";
            case VTDNav.FORMAT_ISO_8859_6 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-6");
            case VTDNav.FORMAT_ISO_8859_7 -> "ISO-8859-7";
            case VTDNav.FORMAT_ISO_8859_8 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-8");
            case VTDNav.FORMAT_ISO_8859_9 -> "ISO-8859-9";
            case VTDNav.FORMAT_ISO_8859_10 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-10");
            case VTDNav.FORMAT_ISO_8859_11 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-11");
            case VTDNav.FORMAT_ISO_8859_12 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-12");
            case VTDNav.FORMAT_ISO_8859_13 -> "ISO-8859-13";
            case VTDNav.FORMAT_ISO_8859_14 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-14");
            case VTDNav.FORMAT_ISO_8859_15 -> "ISO-8859-15";
            case VTDNav.FORMAT_ISO_8859_16 -> throw new IllegalArgumentException("Unsupported encoding: ISO-8859-16");
            case VTDNav.FORMAT_WIN_1250 -> "windows-1250";
            case VTDNav.FORMAT_WIN_1251 -> "windows-1251";
            case VTDNav.FORMAT_WIN_1252 -> "windows-1252";
            case VTDNav.FORMAT_WIN_1253 -> "windows-1253";
            case VTDNav.FORMAT_WIN_1254 -> "windows-1254";
            case VTDNav.FORMAT_WIN_1255 -> throw new IllegalArgumentException("Unsupported encoding: windows-1255");
            case VTDNav.FORMAT_WIN_1256 -> throw new IllegalArgumentException("Unsupported encoding: windows-1256");
            case VTDNav.FORMAT_WIN_1257 -> "windows-1257";
            case VTDNav.FORMAT_WIN_1258 -> throw new IllegalArgumentException("Unsupported encoding: windows-1258");
            default -> throw new IllegalArgumentException("Unknown VTD encoding: " + nav.getEncoding());
        };
    }
}
