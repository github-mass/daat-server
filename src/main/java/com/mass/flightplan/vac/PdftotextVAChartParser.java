//package com.mass.flightplan.vac;
//
//import jakarta.annotation.PostConstruct;
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.SneakyThrows;
//import lombok.extern.log4j.Log4j2;
//import org.apache.pdfbox.io.IOUtils;
//import org.springframework.core.io.Resource;
//import org.springframework.data.util.Pair;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//import org.springframework.util.StreamUtils;
//
//import java.io.*;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.text.DecimalFormat;
//import java.text.ParseException;
//import java.time.Duration;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Optional;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import static java.util.regex.Pattern.*;
//
//@Log4j2
//@RequiredArgsConstructor
//public class PdftotextVAChartParser
//    implements VAChartParser
//{
//    private final @NonNull ThreadPoolTaskExecutor executor;
//    private final @NonNull Duration timeout;
//    private final @NonNull VACAtlasProperties atlasProperties;
//    private final boolean keepFiles;
//
//    private Path tmpDir;
//
//    @PostConstruct
//    private void init()
//        throws IOException
//    {
//        if (keepFiles) {
//            tmpDir = Files.createTempDirectory("flightplan-vac-pdf-extraction");
//        }
//    }
//
//    private void maybeKeepOutput(Resource pdf, String code, CharSequence pdfText) {
//        if (keepFiles) {
//            try {
//                try (InputStream is = pdf.getInputStream();
//                     OutputStream os = Files.newOutputStream(Files.createTempFile(tmpDir, code, ".pdf"))) {
//                    StreamUtils.copy(is, os);
//                }
//
//                try (BufferedWriter bw = Files.newBufferedWriter(Files.createTempFile(tmpDir, code, ".txt"))) {
//                    bw.write(pdfText.toString());
//                }
//            }
//            catch (Exception x) {
//                log.warn("Failed to store VAC PDF extraction output for " + code, x);
//            }
//        }
//    }
//
//    @Override
//    public AirportInfo parseAirportInfo(Resource vAChartPdf, String code, String name)
//        throws IOException, InterruptedException
//    {
//        CharSequence pdfText = invokePdfToText(vAChartPdf, executor, timeout);
//        maybeKeepOutput(vAChartPdf, code, pdfText);
//
//        var builder = AirportInfo.builder()
//                                 .code(code).name(name)
//                                 .eAipVersion(atlasProperties.getEAipVersion());
//
//        var alt = find(pdfText, VAC_ALT_PATTERN)
//            .map(m -> m.group(1)).map(Integer::parseInt)
//            .orElseThrow(() -> new IllegalArgumentException("Could not find altitude in VAC PDF text"));
//        builder.altitude(alt);
//
//        var qfe = find(pdfText, VAC_QFE_PATTERN)
//            .map(m -> m.group(1)).map(Integer::parseInt)
//            .orElseThrow(() -> new IllegalArgumentException("Could not find local pressure in VAC PDF text"));
//        builder.localPressure(qfe);
//
//        var lat = find(pdfText, LAT_PATTERN)
//            .map(m ->
//                (m.group(4).equals("S") ? -1d : 1d)
//                    * (
//                    parsePadded(m.group(1), 2)
//                        + parsePadded(m.group(2), 2) / 60
//                        + parsePadded(m.group(3), 2) / 3600
//                )
//            )
//            .orElseThrow(() -> new IllegalArgumentException("Could not find latitude in VAC PDF text"));
//        builder.latitude(lat);
//
//        var lon = find(pdfText, LONG_PATTERN)
//            .map(m ->
//                (m.group(4).equals("W") ? -1d : 1d)
//                    * (
//                    parsePadded(m.group(1), 3)
//                        + parsePadded(m.group(2), 2) / 60
//                        + parsePadded(m.group(3), 2) / 3600
//                )
//            )
//            .orElseThrow(() -> new IllegalArgumentException("Could not find longitude in VAC PDF text"));
//        builder.longitude(lon);
//
//        var contact = find(pdfText, INDEXED_CONTACT_INFO_PATTERN)
//            .or(() -> find(pdfText, NON_INDEXED_CONTACT_INFO_PATTERN))
//            .map(m -> m.group(1).trim())
//            .orElseThrow(() -> new IllegalArgumentException("Could not find contact info in VAC PDF text"));
//        builder.contactInfo(contact);
//
//        List<RunwayInfo> rws = new ArrayList<>();
//        Matcher m = VAC_RUNWAY_PATTERN.matcher(pdfText);
//        while (m.find()) {
//            var rw = RunwayInfo.builder()
//                               .shortOrientation(Pair.of(m.group(1), m.group(2)))
//                               .magneticOrientation(Pair.of((int)parsePadded(m.group(3), 3), (int)parsePadded(m.group(4), 3)))
//                               .length(Integer.parseInt(m.group(5).split("\\D+")[0]))
//                               .width(Integer.parseInt(m.group(5).split("\\D+")[1]))
//                               .paved(!m.group(6).toLowerCase().contains("unpaved"))
//                               .build();
//
//            rws.add(rw);
//        }
//
//        if (rws.isEmpty()) {
//            throw new IllegalArgumentException("Could not find runways in VAC PDF text");
//        }
//        else {
//            builder.runways(rws);
//        }
//
//        return builder.build();
//    }
//
//    @Override
//    public HelipadInfo parseHeliportInfo(Resource vAChartPdf, String code, String name)
//        throws IOException, InterruptedException
//    {
//        CharSequence pdfText = invokePdfToText(vAChartPdf, executor, timeout);
//        maybeKeepOutput(vAChartPdf, code, pdfText);
//
//        var builder = HelipadInfo.builder()
//                                 .code(code).name(name)
//                                 .eAipVersion(atlasProperties.getEAipVersion());
//
//        var alt = find(pdfText, HVAC_ALT_PATTERN)
//            .map(m -> m.group(1)).map(Integer::parseInt)
//            .orElseThrow(() -> new IllegalArgumentException("Could not find altitude in VAC PDF text"));
//        builder.altitude(alt);
//
//        var qfe = find(pdfText, HVAC_QFE_PATTERN)
//            .map(m -> m.group(1)).map(Integer::parseInt)
//            .orElseThrow(() -> new IllegalArgumentException("Could not find local pressure in VAC PDF text"));
//        builder.localPressure(qfe);
//
//        var lat = find(pdfText, LAT_PATTERN)
//            .map(m ->
//                (m.group(4).equals("S") ? -1d : 1d)
//                    * (
//                    parsePadded(m.group(1), 2)
//                        + parsePadded(m.group(2), 2) / 60
//                        + parsePadded(m.group(3), 2) / 3600
//                )
//            )
//            .orElseThrow(() -> new IllegalArgumentException("Could not find latitude in VAC PDF text"));
//        builder.latitude(lat);
//
//        var lon = find(pdfText, LONG_PATTERN)
//            .map(m ->
//                (m.group(4).equals("W") ? -1d : 1d)
//                    * (
//                    parsePadded(m.group(1), 3)
//                        + parsePadded(m.group(2), 2) / 60
//                        + parsePadded(m.group(3), 2) / 3600
//                )
//            )
//            .orElseThrow(() -> new IllegalArgumentException("Could not find longitude in VAC PDF text"));
//        builder.longitude(lon);
//
//        var contact = find(pdfText, INDEXED_CONTACT_INFO_PATTERN)
//            .or(() -> find(pdfText, NON_INDEXED_CONTACT_INFO_PATTERN))
//            .map(m -> m.group(1).trim())
//            .orElseThrow(() -> new IllegalArgumentException("Could not find contact info in VAC PDF text"));
//        builder.contactInfo(contact);
//
//        return builder.build();
//    }
//
//    private static Optional<Matcher> find(CharSequence cs, Pattern pattern) {
//        Matcher m = pattern.matcher(cs);
//        return m.find() ? Optional.of(m) : Optional.empty();
//    }
//
//    private static final Pattern
//        HVAC_ALT_PATTERN = Pattern.compile("^ALT [^:]*: (\\d+)", MULTILINE),
//        VAC_ALT_PATTERN = HVAC_ALT_PATTERN,
//        //we can have "ALT : \d+ ft (\d hPa)" in here. Or not 'ft', or sometimes "ALT AD"...
//        HVAC_QFE_PATTERN = Pattern.compile("^ALT [^:]*: \\d+[^(]+\\((\\d+) ?hPa\\)", MULTILINE),
//        VAC_QFE_PATTERN = HVAC_QFE_PATTERN,
//        LAT_PATTERN = Pattern.compile("^LAT(?:\\s|\\v)+:(?:\\s|\\v)+(\\d{2}) (\\d{2}) (\\d{2}) ([NS])", MULTILINE),
//        LONG_PATTERN = Pattern.compile("^LONG(?:\\s|\\v)+:(?:\\s|\\v)+(\\d{3}) (\\d{2}) (\\d{2}) ([EW])", MULTILINE),
//        INDEXED_CONTACT_INFO_PATTERN = Pattern.compile("^\\d+ *-\\V*(?:\\boperator\\b|\\badministrator\\b)\\V*?:(.*?)^\\d+\\s?-", CASE_INSENSITIVE | MULTILINE | DOTALL),
//        NON_INDEXED_CONTACT_INFO_PATTERN = Pattern.compile("(?:^\\d+ *-)?\\V*(?:\\boperator\\b|\\badministrator\\b)\\V*?:(.*)(^\\d+\\s?-|^\\S)", CASE_INSENSITIVE | MULTILINE | DOTALL),
//        VAC_RUNWAY_PATTERN = Pattern.compile(
//            "(\\d{2}[LR]?)\\v+(\\d{2}[LR]?)\\v+(\\d{3})\\v+(\\d{3})\\v+(\\d+\\D+\\d+)\\v+(\\V+\\v+\\V+)"
//        );
//
//    private static final String LINE_SEP = System.getProperty("line.separator");
//
//    @SneakyThrows(ParseException.class)
//    private static double parsePadded(String text, int digitCount) {
//        char[] c = new char[digitCount];
//        Arrays.fill(c, '0');
//        return new DecimalFormat(String.valueOf(c)).parse(text).intValue();
//    }
//
//    protected CharSequence invokePdfToText(Resource pdf, ThreadPoolTaskExecutor executor, Duration timeout)
//        throws IOException, InterruptedException
//    {
//        // UTF-8 is default encoding but I want to keep a reminder
//        List<String> cmd = List.of("pdftotext", "-nodiag", "-enc", "UTF-8", "-nopgbrk", "-", "-");
//
//        log.debug("Starting pdftotext process for {} ", pdf);
//        Process p = createProcess(cmd);
//
//        Future<Void> feeder = executor.submit(() -> {
//            OutputStream processInput = p.getOutputStream();
//            try (InputStream is = pdf.getInputStream()) {
//                IOUtils.copy(is, processInput);
//            }
//
//            processInput.close();
//            return null;
//        });
//
//        Future<CharSequence> collector = executor.submit(() -> {
//            StringBuilder sb = new StringBuilder();
//            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
//                for (String line; null != (line = br.readLine()); ) {
//                    sb.append(line); sb.append(LINE_SEP);
//                }
//            }
//
//            return sb;
//        });
//
//        try {
//            CharSequence cs = collector.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
//
//            if (p.exitValue() != 0) {
//                throw new IOException("pdftotext process failed with code " + p.exitValue());
//            }
//
//            return cs;
//        }
//        catch (InterruptedException iex) {
//            p.destroy();
//            throw iex;
//        }
//        catch (TimeoutException tex) {
//            p.destroy();
//            throw new IOException("pdftotext process timed out after " + timeout);
//        }
//        catch (ExecutionException eex) {
//            throw new IOException("pdftotext conversion failed", eex.getCause());
//        }
//    }
//
//    protected Process createProcess(List<String> cmd)
//        throws IOException
//    {
//        ProcessBuilder pb = new ProcessBuilder(cmd);
//
//        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
//
//        return pb.start();
//    }
//}
