package com.mass.flightplan.vac;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.geotools.measure.Units;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import javax.measure.UnitConverter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mass.flightplan.vac.TextBlock.Comp;
import static com.mass.flightplan.vac.TextBlock.Utils;
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

@Log4j2
@RequiredArgsConstructor
public class BlockVAChartParser
    implements VAChartParser
{

    @NonNull
    private final VacPdfExtractor pdfExtractor;
    @NonNull
    private final VACAtlasProperties atlasProperties;

    private final boolean keepFiles;

    private Path tmpDir;

    private UnitConverter FEET_TO_METRES;


    @PostConstruct
    private void init()
        throws IOException
    {
        if (keepFiles) {
            tmpDir = Files.createTempDirectory("flightplan-vac-pdf");
        }

        FEET_TO_METRES = Units.FOOT.getConverterTo(Units.METRE);
    }

    private void maybeKeepOutput(Resource pdf, String code, VacPdfExtractor.Result extractionResult) {
        if (keepFiles) {
            try {
                try (InputStream is = pdf.getInputStream();
                     OutputStream os = Files.newOutputStream(Files.createTempFile(tmpDir, code, ".pdf"))) {
                    StreamUtils.copy(is, os);
                }

                Path p = Files.createTempFile(tmpDir, code + "-blocks", ".txt");
                try (var w = Files.newBufferedWriter(p)) {
                    List<String> lines =
                        extractionResult.blocksMergedInFlow().stream()
                                        .sorted(Comp.TOP_FIRST_THEN_LEFT)
                                        .map(b -> String.format("%s%n%s", b.boundsToString(), b.text()))
                                        .toList();

                    for (String line : lines) {
                        w.write(line);
                        w.newLine();
                    }
                }

                try (BufferedWriter bw = Files.newBufferedWriter(Files.createTempFile(tmpDir, code, ".txt"))) {
                    for (TextBlock block : extractionResult.blocksMergedInPage()) {
                        bw.write(block.text());
                        bw.newLine();
                    }
                }
            }
            catch (Exception x) {
                log.warn("Failed to store VAC PDF extraction output for " + code, x);
            }
        }
    }

    @Override
    public AirportInfo parseAirportInfo(Resource vAChartPdf, String code, String name)
        throws IOException, InterruptedException
    {
        VacPdfExtractor.Result extractionResult = pdfExtractor.extractPdfText(vAChartPdf);
        maybeKeepOutput(vAChartPdf, code, extractionResult);

        var builder = AirportInfo.builder();

        builder.code(code).name(name);
        builder.eAipVersion(atlasProperties.getEAipVersion());

        Collection<? extends TextBlock> blocks = extractionResult.blocksMergedInPage();

        var alt = find(blocks, ALTITUDE_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find altitude in VAC PDF text"));

        // Convert altitude to meters
        builder.altitude(FEET_TO_METRES.convert(alt).doubleValue());

        var psi = find(blocks, QFE_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find local pressure in VAC PDF text"));
        builder.localPressure(psi);

        var magDec = find(blocks, MAG_DEC_PATTERN)
            .or(() -> find(blocks, MAG_DEC_PATTERN_IDIOT))
            .map(m -> (m.group(2).equals("W") ? -1 : 1) * parseInt(m.group(1)))
            .orElseThrow(() -> new IllegalArgumentException("Could not find magnetic declination in VAC PDF text"));
        builder.magneticDeclination(magDec);

        var lat = find(blocks, LAT_PATTERN)
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

        var lon = find(blocks, LON_PATTERN)
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

        try {
            builder.runways(extractRunways(extractionResult));
        }
        catch (RuntimeException x) {
            if(atlasProperties.getIgnoreRunwayExtractionErrors().contains(code)){
                log.info("Ignoring failure to extract runways from VAC for " + code + " because it's in our ignore list");
                builder.runways(List.of());
            }
            else {
                throw x;
            }
        }

        var contact = extractAirportContact(extractionResult)
            .map(CharSequence::toString)
            .orElseThrow(() -> new IllegalArgumentException("Could not find contact info in VAC for " + code));

        builder.contactInfo(contact.trim());

        return builder.build();
    }

    private static Map<String, TextBlock> findRunwayTableHeaders(VacPdfExtractor.Result extractionResult) {
        List<TextBlock> rwyWords = extractionResult.words().stream().filter(Utils.textIs("RWY")).toList();
        //These fuckers are going to kill me I swear (QFU/QF)
        List<TextBlock> qfuWords = extractionResult.words().stream().filter(b -> b.text().matches("QFU?")).toList();

        Map<String, TextBlock> ret = new HashMap<>();
        OUT:
        for (TextBlock rwy : rwyWords) {
            for (TextBlock qfu : qfuWords) {
                if (rwy.vRelDist(qfu) < -.5 && rwy.hAbsDist(qfu) < 1d / 15) {
                    ret.put("rwy", rwy);
                    ret.put("qfu", qfu);

                    List<TextBlock> dimBlocks = extractionResult.words().stream().filter(b -> b.text().equalsIgnoreCase("dimension")).toList();
                    for (TextBlock dim : dimBlocks) {
                        if (dim.vAbsDist(qfu) < 1d / 20 && dim.hAbsDist(qfu) < 2d / 15) {
                            ret.put("dimension", dim);

                            List<TextBlock> sfcBlocks = extractionResult.words().stream().filter(b -> b.text().equalsIgnoreCase("nature")).toList();
                            for (TextBlock sfc : sfcBlocks) {
                                if (sfc.vAbsDist(dim) < 1d / 20 && sfc.hAbsDist(dim) < 2d / 15) {
                                    ret.put("surface", sfc);

                                    break OUT;
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    private static List<RunwayInfo> extractRunways(VacPdfExtractor.Result extractionResult) {

        /*
            We'll extract 4 columns: RWY, QFU, Dimensions and Surface.
            We'll try to use the flow-segregated version first,
            then fall back on blocks (pdftotext lines) if it fails.
         */

        Map<String, TextBlock> rwyTableHeaders = findRunwayTableHeaders(extractionResult);

        try {
            return tryExtractRunways(rwyTableHeaders, extractionResult.blocksMergedInFlow());
        }
        catch (RuntimeException rex) {
            try {
                return tryExtractRunways(rwyTableHeaders, extractionResult.blocks());
            }
            catch (Throwable t) {
                throw rex;
            }
        }
    }

    private static List<RunwayInfo> tryExtractRunways(
        final Map<String, TextBlock> tableHeaders, final Collection<? extends TextBlock> blocks
    ){

        TextBlock rwyBlock = Optional.ofNullable(tableHeaders.get("rwy"))
                                     .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'RWY'"));
        TextBlock qfuBlock = Optional.ofNullable(tableHeaders.get("qfu"))
                                     .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'QFU'"));
        TextBlock dimBlock = Optional.ofNullable(tableHeaders.get("dimension"))
                                     .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'Dimension'"));
        TextBlock sfcBlock = Optional.ofNullable(tableHeaders.get("surface"))
                                     .orElseThrow(() -> new NoSuchElementException("Could not find block with text 'Surface'"));

        Map<TextBlock, String> rwyList, qfuList;

        /*
            Will prob. break if the table ever spans multiple pages.
         */
        /*
            We have cases where RWY and QFU headers are parsed as one block, and then the lines too.

            We can also sometimes have A FRICKING ARROW in front of the thing

            Excluding the colon in rwyFrag to try to sift out lines we get from below the table.
         */
        final String rwyFrag = "\\W*(\\d{2} ?[LRC]?)\\b[^:]*", qfuFrag = "(\\d{3})°?";
        Pattern rwyAndOrQfu = Pattern.compile(
            String.format("^%1$s$|^%2$s$|^%1$s\\s+%2$s$", rwyFrag, qfuFrag)
        );
        final BiPredicate<TextBlock, TextBlock> RWY_AND_QFU_TABLE_H_PREDICATE =
            Utils.hRelDistMax(-0.05).and((header, block) -> rwyAndOrQfu.matcher(block.text()).matches());

        final var RWY_TABLE_V_PREDICATE = Utils.vAbsDistMax(.075);

        final var biCollector = Collectors.toMap(
            (Object[] o) -> (TextBlock) o[0],
            (Object[] o) -> (String) o[1],
            (o1, o2) -> {
                throw new IllegalArgumentException(o1 + " " + o2);
            },
            LinkedHashMap::new
        );

        AtomicBoolean stopLooking = new AtomicBoolean(false);
        rwyList = blocks.stream()
                        .collect(TextBlock.Utils.extractColumn(rwyBlock, RWY_AND_QFU_TABLE_H_PREDICATE, RWY_TABLE_V_PREDICATE))
                        .stream()
                        .map(b -> {
                            Matcher m = compile(rwyFrag).matcher(b.text());
                            if (m.matches()) {
                                return new Object[]{b, m.group(1).trim()};
                            }
                            else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(biCollector);

        qfuList = blocks.stream()
                        .collect(TextBlock.Utils.extractColumn(qfuBlock, RWY_AND_QFU_TABLE_H_PREDICATE, RWY_TABLE_V_PREDICATE))
                        .stream()
                        .map(b -> {
                            Matcher m = compile(".*?(\\d{3})°?$").matcher(b.text());
                            if (m.matches()) {
                                return new Object[]{b, m.group(1).trim()};
                            }
                            else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(biCollector);

        // Some airports only have length (LFHM, )
        Pattern dimensionPattern = Pattern.compile("\\d+(\\.\\d+)?(\\s*\\(\\d\\))?(\\s*x\\s*\\d+(\\.\\d+)?)?(\\s*m)?(\\s*\\(\\d+\\))?");
        Map<TextBlock, String> dimList = blocks.stream()
                                               .collect(TextBlock.Utils.extractColumn(dimBlock, Utils.hRelDistMax(-.2), RWY_TABLE_V_PREDICATE))
                                               .stream()
                                               .map(b -> {
                                                   Matcher m = dimensionPattern.matcher(b.text());
                                                   if (m.matches()) {
                                                       return new Object[]{b, m.group().trim()};
                                                   }
                                                   else {
                                                       return null;
                                                   }
                                               })
                                               .filter(Objects::nonNull)
                                               .collect(biCollector);

        Map<TextBlock, String> sfcList = blocks.stream()
                                               .collect(TextBlock.Utils.extractColumn(sfcBlock, Utils.hRelDistMax(-.3), RWY_TABLE_V_PREDICATE))
                                               .stream()
                                               .map(b -> {
                                                   if ("surface".equalsIgnoreCase(b.text()) || "nature".equalsIgnoreCase(b.text())) {
                                                       return null;
                                                   }
                                                   else {
                                                       return new Object[]{b, b.text()};
                                                   }
                                               })
                                               .filter(Objects::nonNull)
                                               .collect(biCollector);

//        if(dimList.size() == rwyList.size()){
//            //sometimes these bloody idiots put the dimension for every direction
//            dimList = new LinkedHashSet<>(dimList).stream().toList();
//        }

        /*
            Check our extraction.
        */
        verifyRwyTableExtraction(rwyList, qfuList, dimList, sfcList);

        //should be good, build & ship
        List<RunwayInfo> ret = new ArrayList<>();
        for (TextBlock b_rwy : rwyList.keySet()) {
            var rwy = rwyList.get(b_rwy);

            // find corresponding qfu block
            var qfu = qfuList.keySet().stream()
                             .filter(b -> b.vRelDist(b_rwy) < -.5)
                             .map(qfuList::get).map(BlockVAChartParser::parseQfu)
                             .findAny()
                             .orElseThrow(() -> new IllegalArgumentException("Could not find QFU block for RWY block '" + rwy + "'"));

            // Find sfc entries that are roughly on the same horizontal level as the rwy block
            // ...except they can sometimes be further away (tall column). Find the nearest ones.
            var paved = sfcList.keySet().stream()
                               .sorted(Comp.byRelVDistTo(b_rwy)).limit(2)
                               .map(sfcList::get)
                               .anyMatch(s -> s.equalsIgnoreCase("paved") || s.equalsIgnoreCase("revêtue"));

            // Find dim block that's roughly at the same horizontal level as the rwy block
            // ...except it can sometimes be further away (tall column). Find the nearest one.
            // We don't check for multiples here, as that should have been taken care of earlier
            var dim = dimList.keySet().stream()
                             .sorted(Comp.byRelVDistTo(b_rwy)).limit(1)
                             .map(dimList::get)
                             .findFirst()
                             .orElseThrow(() -> new IllegalArgumentException("Could not find DIM block for RWY block " + rwy));


            double len, width;
            Matcher completeDims = compile("(\\d+(?:\\.\\d+)?).*?(\\d+(?:\\.\\d+)?)(\\s*m)?").matcher(dim);
            if (completeDims.find()) {
                len = Double.parseDouble(completeDims.group(1));
                width = Double.parseDouble(completeDims.group(2));
            }
            else if (dim.matches("(\\d+(?:\\.\\d+)?)")) {
                len = Double.parseDouble(dim);
                width = 0;
            }
            else {
                throw new IllegalArgumentException("Cannot parse runway dimension: " + dim);
            }

            RunwayInfo info = RunwayInfo.builder()
                                        .code(rwy).qfu(qfu).paved(paved).length(len).width(width)
                                        .build();

            ret.add(info);
        }

        if (ret.isEmpty()) {
            throw new IllegalArgumentException("No runways found in VAC");
        }

        return ret;
    }

    private static void verifyRwyTableExtraction(
        Map<TextBlock, String> rwyCol, Map<TextBlock, String> qfuCol,
        Map<TextBlock, String> dimCol, Map<TextBlock, String> sfcCol
    )
    {
        /*
            Common format is two RWY (code) lines, two QFU lines, one DIM line, two SFC lines (one french, one english, paved/unpaved).
            It's commonly two lines because a rwy can generally be used from both ends.
            But in some cases it can't. Then we have a line with one RWY, one QFU, one DIM and two SFC.

            EXCEPT that in some cases SFC is on one line ("[FR] / [EN]")... :-/

            So we'll proceed:
                1. we have N RWY entries
                2. does it match N double entries?
                3. If not, does it match (N-1) double entries + 1 single entry?
                And so forth
        */

        final int minLines = (rwyCol.size() + 1) >> 1, maxLines = rwyCol.size();
        for (int doubles = minLines; doubles >= 0; doubles--) {
            int singles = maxLines - doubles * 2;
            if (rwyCol.size() == doubles * 2 + singles
                && qfuCol.size() == doubles * 2 + singles
                && dimCol.size() == doubles + singles
                && (sfcCol.size() == (doubles + singles) * 2 || sfcCol.size() == doubles + singles)
            ) {
                // OK, that's fine
                log.debug("RWY table: found {} double and {} single lines", doubles, singles);
                return;
            }
        }

        String msg = String.format(
            "RWY table extraction failed. Expected #RWY=#QFU=#SFC=2*#DIM or #RWY=#QFU=#DIM=2*#SFC. Got: #RWY=%d, #QFU=%d, #SFC=%d, #DIM=%d." +
                " RWY: %s; QFU: %s; SFC: %s, DIM: %s",
            rwyCol.size(), qfuCol.size(), sfcCol.size(), dimCol.size(),
            rwyCol.values(), qfuCol.values(), sfcCol.values(), dimCol.values()
        );
        throw new IllegalArgumentException(msg);
    }

    @Override
    public HelipadInfo parseHeliportInfo(Resource vAChartPdf, String code, String name)
        throws IOException, InterruptedException
    {
        VacPdfExtractor.Result extractionResult = pdfExtractor.extractPdfText(vAChartPdf);
        maybeKeepOutput(vAChartPdf, code, extractionResult);

        var builder = HelipadInfo.builder();

        builder.code(code).name(name);
        builder.eAipVersion(atlasProperties.getEAipVersion());

        Collection<? extends TextBlock> blocks = extractionResult.blocksMergedInPage();

        var alt = find(blocks, ALTITUDE_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find altitude in HVAC PDF text"));
        builder.altitude(alt);

        var psi = find(blocks, QFE_PATTERN)
            .map(m -> m.group(1)).map(Integer::parseInt)
            .orElseThrow(() -> new IllegalArgumentException("Could not find local pressure in HVAC PDF text"));
        builder.localPressure(psi);

        var magDec = find(blocks, MAG_DEC_PATTERN)
            .or(() -> find(blocks, MAG_DEC_PATTERN_IDIOT))
            .map(m -> (m.group(2).equals("W") ? -1 : 1) * parseInt(m.group(1)))
            .orElseThrow(() -> new IllegalArgumentException("Could not find magnetic declination in HVAC PDF text"));
        builder.magneticDeclination(magDec);

        var lat = find(blocks, LAT_PATTERN)
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

        var lon = find(blocks, LON_PATTERN)
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

        var contact = extractHelipadContact(extractionResult)
            .map(CharSequence::toString)
            .orElseThrow(() -> new IllegalArgumentException("Could not find contact info in HVAC PDF text"));
        builder.contactInfo(contact.trim());

        return builder.build();
    }

    static Optional<CharSequence> extractHelipadContact(VacPdfExtractor.Result extractionResult)
    {
        Collection<? extends TextBlock> blocks = extractionResult.blocksMergedInPage();

        //we can have arrows at the start of the line sometimes, e.g.:
        // "← 4 - Exploitant d’aérodrome / AD operator"

//        Pattern numberedInfo = Pattern.compile("^\\W*(\\d+)\\s*-\\s*(.*)");
        Pattern operatorInfo = Pattern.compile("^\\W*(\\d+)\\s*-.*(?:operator|administrator|gestionnaire|exploitant d.aérodrome)", CASE_INSENSITIVE);
        Pattern numberedInfoSingle = Pattern.compile("^\\W*(\\d+)\\s*-\\s*(.*)");
        Pattern numberedInfoContinued = Pattern.compile("(\\d+)\\s*-\\s*\\w+.*:");

        Optional<? extends TextBlock> opt = blocks.stream().filter(TextBlock.Utils.find(operatorInfo)).findFirst();
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        TextBlock opBlock = opt.get();

        //Here we'll have either a 2-column layout or a 1-column layout. Let's find out.
        Optional<? extends TextBlock> secondColumn = blocks
            .stream().filter(
                b -> b.left() > opBlock.right()
                    && b.overlapsVertically(opBlock)
            ).findAny();
        boolean twoColumns = secondColumn.isPresent();

        // We can have cases where the operator line is too long and gets merged with the second column line
        twoColumns |= opBlock.text().indexOf(':') != -1 && numberedInfoContinued.matcher(opBlock.text()).find(opBlock.text().indexOf(':'));

        // Minimal overlap
        final BiPredicate<TextBlock, TextBlock> H_PREDICATE = secondColumn.isPresent()
            ? (header, block) -> (block.right() < secondColumn.get().left() - 0.05) || block.left() < header.right()
            : Utils.hRelDistMax(.2);
        final List<TextBlock> miscInfo = blocks.stream().collect(TextBlock.Utils.extractColumn(opBlock, H_PREDICATE, Utils.vRelDistMax(.5), true));

        final StringBuilder sb = new StringBuilder();
        boolean glob = false;
        int operatorInfoNum = -1;

        for (TextBlock block : miscInfo) {
            Matcher m = numberedInfoSingle.matcher(block.text());
            if (m.matches()) {
                if (m.group(2).toLowerCase().contains("operator")
                    || m.group(2).toLowerCase().contains("administrator")
                    || m.group(2).toLowerCase().contains("gestionnaire")
                    || m.group(2).matches("(?i).*exploitant d.aérodrome.*")
                ) {
                    operatorInfoNum = parseInt(m.group(1));
                    glob = true;

                    var s = m.group(2).substring(m.group(2).indexOf(':') + 1).trim();
                    if (!s.isEmpty()) {
                        /*
                            In two-column mode, we will sometimes have the second column appended here,
                            because it's close. Not too big a problem in the grand scheme of things, but
                            let's try to cut out bits if we see a numbered info pattern in here
                         */
                        if (twoColumns) {
                            Matcher m2 = numberedInfoContinued.matcher(s);
                            if (m2.find()) {
                                s = s.substring(0, m2.start());
                            }
                        }

                        sb.append(s);
                        sb.append(LINE_SEP);
                    }
                }
                else if (parseInt(m.group(1)) == operatorInfoNum + 1) {
                    if (glob) break;
                }
            }
            else if (glob) {
                /*
                    In two-column mode, we will sometimes have the second column appended here,
                    because it's close. Not too big a problem in the grand scheme of things, but
                    let's try to cut out bits if we see a numbered info pattern in here
                 */
                String s = block.text();
                if (twoColumns) {
                    Matcher m2 = numberedInfoContinued.matcher(s);
                    if (m2.find()) {
                        s = s.substring(0, m2.start());
                    }
                }

                sb.append(s.trim());
                sb.append(LINE_SEP);
            }
        }

        return sb.isEmpty() ? Optional.empty() : Optional.of(sb);
    }

    static Optional<CharSequence> extractAirportContact(VacPdfExtractor.Result extractionResult)
    {
        return extractHelipadContact(extractionResult);
    }

    static Optional<Matcher> find(Collection<? extends TextBlock> input, Pattern pattern) {
        return input.stream()
                    .filter(TextBlock.Utils.find(pattern))
                    .map(b -> {
                        Matcher m = pattern.matcher(b.text());
                        //noinspection ResultOfMethodCallIgnored
                        m.find();
                        return m;
                    })
                    .findFirst();
    }

    static String matchAndExtract(CharSequence cs, Pattern p, int group) {
        Matcher m = p.matcher(cs);
        if (m.matches()) {
            return m.group(group);
        }
        else {
            throw new IllegalArgumentException(String.format("'%s' does not match '%s'", cs, p.pattern()));
        }
    }

    /*
        Yes, we can have negative altitudes (because QNH?).

        Also, we have cases where there's ALT AD SUP and ALT AD INF (LFIP, non-even runway)
     */
    private static final Pattern ALTITUDE_PATTERN = Pattern.compile("ALT\\s*(?:\\w+\\s*)*:\\s*(-?\\d+)", CASE_INSENSITIVE);
    private static final Pattern QFE_PATTERN = Pattern.compile("\\((\\d+) *hPa\\)");
    private static final Pattern LAT_PATTERN = Pattern.compile("(\\d{2}) *(\\d{2}) *(\\d{2}) *([NS])");
    private static final Pattern LON_PATTERN = Pattern.compile("(\\d{3}) *(\\d{2}) *(\\d{2}) *([EW])");
    private static final Pattern MAG_DEC_PATTERN = compile("VAR\\s*:\\s*(\\d+)°\\s*([EW]?)");
    private static final Pattern MAG_DEC_PATTERN_IDIOT = compile("VAR\\s*:\\s*(\\d+)\\s*([EW])°");

    private static final String LINE_SEP = "\n";

    @SneakyThrows(ParseException.class)
    private static double parsePadded(String text, int digitCount) {
        char[] c = new char[digitCount];
        Arrays.fill(c, '0');
        return new DecimalFormat(String.valueOf(c)).parse(text).doubleValue();
    }

    private static int parseQfu(String qfu) {
        int end;
        //noinspection StatementWithEmptyBody
        for (end = qfu.length(); qfu.charAt(end - 1) == '°'; --end) ;
        int start;
        //noinspection StatementWithEmptyBody
        for (start = 0; start < end - 1 && qfu.charAt(start) == '0'; ++start) ;
        return parseInt(qfu, start, end, 10);
    }
}
