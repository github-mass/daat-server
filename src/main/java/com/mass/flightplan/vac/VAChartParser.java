package com.mass.flightplan.vac;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Component
@Log4j2
@RequiredArgsConstructor
public class VAChartParser {

    @NonNull
    private final VacPdfExtractor pdfExtractor;

    public AirportInfo parseAirportInfo(Resource vAChartPdf, String code, String name)
        throws IOException, InterruptedException
    {
        Collection<? extends OcrBlock> pdfTextBlocks = pdfExtractor.extractPdfText(vAChartPdf);

        var builder = AirportInfo.builder();

        builder.code(code).name(name);

        var alt = find(pdfTextBlocks, ALTITUDE_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find altitude in VAC PDF text"));
        builder.altitude(alt);

        var psi = find(pdfTextBlocks, PSI_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find local pressure in VAC PDF text"));
        builder.localPressure(psi);

        var lat = find(pdfTextBlocks, LAT_PATTERN)
            .map(m ->
                (m.group(4).equals("S") ? -1d : 1d)
                    * (
                    parsePadded(m.group(1), 2)
                        + parsePadded(m.group(2), 2) / 60
                        + parsePadded(m.group(3), 2) / 3600
                )
            )
            .orElseThrow(() -> new IllegalArgumentException("Could not find latitude in VAC PDF text"));
        builder.latitude(lat);

        var lon = find(pdfTextBlocks, LON_PATTERN)
            .map(m ->
                (m.group(4).equals("W") ? -1d : 1d)
                    * (
                    parsePadded(m.group(1), 3)
                        + parsePadded(m.group(2), 2) / 60
                        + parsePadded(m.group(3), 2) / 3600
                )
            )
            .orElseThrow(() -> new IllegalArgumentException("Could not find longitude in VAC PDF text"));
        builder.longitude(lon);

        builder.runways(
            extractRunways(pdfTextBlocks)
                .orElseThrow(() -> new IllegalArgumentException("Could not find runways in VAC PDF text"))
        );

        var contact = extractAirportContact(pdfTextBlocks)
            .map(CharSequence::toString)
            .orElseThrow(() -> new IllegalArgumentException("Could not find contact info in VAC PDF text"));
        builder.contactInfo(contact.trim());

        return builder.build();
    }

    private static Optional<List<RunwayInfo>> extractRunways(Collection<? extends OcrBlock> blocks) {
        /*
            We'll extract 4 columns: RWY, QFU, Dimensions and Surface
         */

        OcrBlock rwyBlock = blocks.stream().filter(b -> b.text().equals("RWY")).findFirst()
                                  .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'RWY'"));
        OcrBlock qfuBlock = blocks.stream().filter(b -> b.text().equals("QFU")).findFirst()
                                  .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'QFU'"));
        OcrBlock dimBlock = blocks.stream().filter(b -> b.text().equals("Dimension")).findFirst()
                                  .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'Dimension'"));
        OcrBlock sfcBlock = blocks.stream().filter(b -> b.text().equals("Surface")).findFirst()
                                  .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'Surface'"));

        /*
            Note that we're replacing 'O' with 0 on the fly on the columns where we expect only digits
         */
        /*
            Based on an A4 page size, our tolerances here mean: H=4.2mm, V=15mm.
            Will prob. break if the table ever spans multiple pages.
         */
        List<String> rwyList = blocks.stream()
                                     .collect(OcrBlock.Utils.extractColumn(rwyBlock, .02, .1))
                                     .stream().map(OcrBlock::text).map(s -> s.replace('O', '0')).toList();

        List<String> qfuList = blocks.stream()
                                     .collect(OcrBlock.Utils.extractColumn(qfuBlock, .02, .1))
                                     .stream().map(OcrBlock::text).map(s -> s.replace('O', '0')).toList();

        List<String> dimensionsList = blocks.stream()
                                            .collect(OcrBlock.Utils.extractColumn(dimBlock, .02, .1))
                                            .stream().map(OcrBlock::text).map(s -> s.replace('O', '0')).toList();

        List<String> surfaceList = blocks.stream()
                                         .collect(OcrBlock.Utils.extractColumn(sfcBlock, .02, .1))
                                         .stream().map(OcrBlock::text).toList();

        //check our extraction
        if (rwyList.size() != surfaceList.size() || rwyList.size() != qfuList.size() || rwyList.size() != dimensionsList.size() * 2) {
            String msg = String.format(
                "RWY table extraction failed. Expected #RWY=#QFU=#SFC=2*#DIM. Got: #RWY=%d, #QFU=%d, #SFC=%d, #DIM=%d." +
                    " RWY: %s; QFU: %s; DIM: %s;SFC: %s",
                rwyList.size(), qfuList.size(), surfaceList.size(), dimensionsList.size(),
                rwyList, qfuList, surfaceList, dimensionsList
            );
            throw new IllegalArgumentException(msg);
        }

        //should be good, build & ship
        List<RunwayInfo> ret = new ArrayList<>();
        for (int ii = 0; ii < dimensionsList.size(); ii++) {
            var rwy = Pair.of(rwyList.get(ii * 2), rwyList.get(ii * 2 + 1));
            var qfu = Pair.of((int) parsePadded(qfuList.get(ii * 2), 3), (int) parsePadded(qfuList.get(ii * 2 + 1), 3));
            var paved = !surfaceList.get(ii * 2 + 1).contains("Unpaved");
            String[] dims = dimensionsList.get(ii).split("\\D+");

            RunwayInfo info = RunwayInfo.builder()
                                        .shortOrientation(rwy)
                                        .magneticOrientation(qfu)
                                        .paved(paved)
                                        .length(Integer.parseInt(dims[0]))
                                        .width(Integer.parseInt(dims[1]))
                                        .build();

            ret.add(info);
        }

        return ret.isEmpty() ? Optional.empty() : Optional.of(ret);
    }

    public HeliportInfo parseHeliportInfo(Resource vAChartPdf, String code, String name)
        throws IOException, InterruptedException
    {
        Collection<? extends OcrBlock> pdfTextBlocks = pdfExtractor.extractPdfText(vAChartPdf);

        var builder = HeliportInfo.builder();

        builder.code(code).name(name);

        var alt = find(pdfTextBlocks, ALTITUDE_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find altitude in HVAC PDF text"));
        builder.altitude(alt);

        var psi = find(pdfTextBlocks, PSI_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find local pressure in HVAC PDF text"));
        builder.localPressure(psi);

        var lat = find(pdfTextBlocks, LAT_PATTERN)
            .map(m ->
                (m.group(4).equals("S") ? -1d : 1d)
                    * (
                    parsePadded(m.group(1), 2)
                        + parsePadded(m.group(2), 2) / 60
                        + parsePadded(m.group(3), 2) / 3600
                )
            )
            .orElseThrow(() -> new IllegalArgumentException("Could not find latitude in HVAC PDF text"));
        builder.latitude(lat);

        var lon = find(pdfTextBlocks, LON_PATTERN)
            .map(m ->
                (m.group(4).equals("W") ? -1d : 1d)
                    * (
                    parsePadded(m.group(1), 3)
                        + parsePadded(m.group(2), 2) / 60
                        + parsePadded(m.group(3), 2) / 3600
                )
            )
            .orElseThrow(() -> new IllegalArgumentException("Could not find longitude in HVAC PDF text"));
        builder.longitude(lon);

        var contact = extractHelipadContact(pdfTextBlocks)
            .map(CharSequence::toString)
            .orElseThrow(() -> new IllegalArgumentException("Could not find contact info in HVAC PDF text"));
        builder.contactInfo(contact.trim());

        return builder.build();
    }

    Optional<CharSequence> extractHelipadContact(Collection<? extends OcrBlock> blocks)
    {
        Pattern operatorInfo = Pattern.compile("^(\\d+)\\s*[-.]\\s*.*operator", CASE_INSENSITIVE);
        Pattern numberedInfo = Pattern.compile("^(\\d+)\\s*[-.]\\s*(.*)");

        Optional<? extends OcrBlock> opt = blocks.stream().filter(OcrBlock.Utils.find(operatorInfo)).findFirst();
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        OcrBlock opBlock = opt.get();

        //Here we'll have either a 2-column layout or a 1-column layout. Let's find out.
        boolean twoColumns = blocks.stream().anyMatch(
            b -> b.left() > opBlock.right()
                && b.overlapsVertically(opBlock)
                && numberedInfo.matcher(b.text()).find()
        );

        StringBuilder sb = new StringBuilder();

        // Use full hTolerance
        List<OcrBlock> miscInfo = blocks.stream().collect(OcrBlock.Utils.extractColumn(opBlock, 1, .05, true));
        boolean glob = false;
        int operatorInfoNum = -1;

        for (OcrBlock block : miscInfo) {
            Matcher m = numberedInfo.matcher(block.text());
            if (m.matches()) {
                if (m.group(2).toLowerCase().contains("operator")) {
                    operatorInfoNum = Integer.parseInt(m.group(1));
                    glob = true;

                    var s = m.group(2).substring(m.group(2).lastIndexOf(':')+1).trim();
                    if (!s.isEmpty()) {
                        sb.append(s);
                        sb.append(LINE_SEP);
                    }
                }
                else if (Integer.parseInt(m.group(1)) == operatorInfoNum + 1) {
                    glob = false;
                }
            }
            else if (glob) {
                //only grab if only one column or if we have hOverlap
                if (!twoColumns || block.overlapsHorizontally(opBlock)) {
                    sb.append(block.text());
                    sb.append(LINE_SEP);
                }
            }
        }

        return sb.isEmpty() ? Optional.empty() : Optional.of(sb);
    }

    static Optional<CharSequence> extractAirportContact(Collection<? extends OcrBlock> blocks)
    {
        Optional<? extends OcrBlock> misc = blocks.stream().filter(OcrBlock.Utils.contains("Miscellaneous")).findFirst();

        if (misc.isEmpty()) {
            log.debug("No block with text 'Miscellaneous' found in input");
            return Optional.empty();
        }

        List<OcrBlock> column = blocks.stream().collect(OcrBlock.Utils.extractColumn(misc.get(), 1, 0.1));

        StringBuilder sb = new StringBuilder();
        boolean on = false;
        Pattern numberedInfo = Pattern.compile("^(\\d+)\\s*[-.]\\s*(.*)$");

        for (OcrBlock block : column) {
            Matcher m = numberedInfo.matcher(block.text());

            if (m.matches()) {
                if (m.group(2).contains("AD operator")) {
                    on = true;
                    int idx = block.text().indexOf("AD operator");
                    idx = block.text().indexOf(':', idx);
                    sb.append(block.text().substring(idx + 1).trim());
                    sb.append(LINE_SEP);
                }
                else {
                    on = false;
                }
            }
            else if (on) {
                sb.append(block.text());
                sb.append(LINE_SEP);
            }
        }

        return sb.isEmpty() ? Optional.empty() : Optional.of(sb);
    }

    private Optional<Matcher> find(Collection<? extends OcrBlock> input, Pattern pattern) {
        return input.stream()
                    .filter(OcrBlock.Utils.find(pattern))
                    .map(b -> {
                        Matcher m = pattern.matcher(b.text());
                        m.find();
                        return m;
                    })
                    .findFirst();
    }

    private static final Pattern ALTITUDE_PATTERN = Pattern.compile("ALT\\s*(?:AD\\s*)?:\\s*(\\d+)\\s*\\(", CASE_INSENSITIVE);
    private static final Pattern PSI_PATTERN = Pattern.compile("\\((\\d+) hPa\\)");
    private static final Pattern LAT_PATTERN = Pattern.compile("(\\d{2}) +(\\d{2}) +(\\d{2}) +([NS])");
    private static final Pattern LON_PATTERN = Pattern.compile("(\\d{3}) +(\\d{2}) +(\\d{2}) +([EW])");

    private static final String LINE_SEP = System.getProperty("line.separator");

    @SneakyThrows(ParseException.class)
    private static double parsePadded(String text, int digitCount) {
        char[] c = new char[digitCount];
        Arrays.fill(c, '0');
        return new DecimalFormat(String.valueOf(c)).parse(text).doubleValue();
    }
}
