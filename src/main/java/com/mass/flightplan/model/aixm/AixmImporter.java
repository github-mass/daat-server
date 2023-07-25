package com.mass.flightplan.model.aixm;

import com.mass.flightplan.geo.AltitudeService;
import com.mass.flightplan.model.Dataset;
import com.mass.flightplan.util.XPathDocumentExtractor;
import com.ximpleware.NavException;
import com.ximpleware.VTDNav;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.event.Level;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.Math.min;
import static java.time.Instant.now;

@Builder
@Slf4j
public class AixmImporter {

    public static final String DATASET_TYPE = "SIA/AIXM";

    private final @NonNull String sourceName;
    private final @Nullable String sourceDescription;
    private final @NonNull Resource source;

    private final AltitudeService altitudeService;

    private boolean parseSiaExport;

    @Value
    @Builder
    public static class Result {
        Dataset dataset;
        @Singular
        List<Airspace> airspaces;
        @Singular
        List<Heliport> heliports;
        @Singular
        List<Aerodrome> aerodromes;
    }

    public Result perform()
        throws Exception
    {
        return isSourceDirectory() ? performDirectoryImport() : performZipImport();
    }

    boolean isSourceDirectory(){
        try {
            return source.isFile() && source.getFile().isDirectory();
        }
        catch (IOException e) {
            return false;
        }
    }

    Result performDirectoryImport()
        throws Exception
    {
        log.atLevel(Level.INFO).log("Starting DIRECTORY import from {}", source);
        Instant start = now();

        File dir = source.getFile().getAbsoluteFile();

        if(!dir.isDirectory()){
            throw new IllegalArgumentException("Expected directory as resource, but got: " + source);
        }

        File aixmFile = null, siaFile = null;

        for(File file: dir.listFiles()){
            if(file.isFile()) {
                SiaXmlFileType type = SiaXmlFileMagic.identify(file);
                switch(type){
                    case AIXM_SNAPSHOT -> aixmFile = file;
                    case SIA_EXPORT -> siaFile = file;
                }

                if(aixmFile != null && siaFile != null){
                    break;
                }
            }
        }

        if(aixmFile == null){
            throw new IllegalStateException("Could not find AIXM file in directory: " + dir);
        }
        if(siaFile == null && parseSiaExport){
            throw new IllegalStateException("Could not find SIA file in directory: " + dir);
        }

        Instant s2 = now();
        log.atLevel(Level.INFO).log("Loading AIXM data from {}", aixmFile);
        XPathDocumentExtractor extractor = XPathDocumentExtractor.from(aixmFile.toPath()).namespaceAware(false).build();
        log.atLevel(Level.INFO).log("Done loading AIXM data from {} in {}s", aixmFile, Duration.between(s2, now()).toMillis() / 1000d);

        Dataset ds = prepareDataset(extractor);

        var builder = Result.builder();
        builder.dataset(ds);
        performAixmImport(extractor, builder);

        if(siaFile != null){
            s2 = now();
            log.atLevel(Level.INFO).log("Loading SIA data from {}", siaFile);
            extractor = XPathDocumentExtractor.from(siaFile.toPath()).namespaceAware(false).build();
            log.atLevel(Level.INFO).log("Done loading SIA data from {} in {}s", siaFile, Duration.between(s2, now()).toMillis() / 1000d);

            performHelipadAdminUpdate(extractor, builder);
        }

        log.atLevel(Level.INFO).log("DIRECTORY import complete in {}s", Duration.between(start, now()).toMillis() / 1000d);

        return builder.build();
    }

    Result performZipImport()
        throws Exception
    {
        log.atLevel(Level.INFO).log("Starting ZIP import from {}", source);
        Instant start = now();

        XPathDocumentExtractor aixmExtractor = null, siaExtractor = null;

        Instant s2 = now();
        try(InputStream is = source.getInputStream(); ZipInputStream zis = new ZipInputStream(is))
        {
            for(ZipEntry ze; null != (ze = zis.getNextEntry()); zis.closeEntry()){
                ByteArrayOutputStream baos = new ByteArrayOutputStream((int) ze.getSize());
                byte[] buf = new byte[256];
                for(int toread = (int) ze.getSize(), read;
                    toread > 0 && -1 != (read = zis.read(buf, 0, min(toread, buf.length)));
                    toread -= read
                    ){
                    baos.write(buf, 0, read);
                }

                ByteArrayResource res = new ByteArrayResource(baos.toByteArray(), "ZIP entry: " + ze.getName());
                switch(SiaXmlFileMagic.identify(res)){
                    case AIXM_SNAPSHOT:
                        aixmExtractor = XPathDocumentExtractor.from(res).namespaceAware(false).build();
                        break;
                    case SIA_EXPORT:
                        if(parseSiaExport){
                            siaExtractor = XPathDocumentExtractor.from(res).namespaceAware(false).build();
                            break;
                        }
                }
            }
        }

        if(aixmExtractor == null){
            throw new IllegalStateException("Could not find AIXM file in archive: " + source);
        }
        if(siaExtractor == null && parseSiaExport){
            throw new IllegalStateException("Could not find SIA file in archive: " + source);
        }

        log.atLevel(Level.INFO).log("Done loading data from {} in {}s", source, Duration.between(s2, now()).toMillis() / 1000d);

        Dataset ds = prepareDataset(aixmExtractor);

        var builder = Result.builder();
        builder.dataset(ds);
        performAixmImport(aixmExtractor, builder);

        if(siaExtractor != null){
            performHelipadAdminUpdate(siaExtractor, builder);
        }

        log.atLevel(Level.INFO).log("ZIP import complete in {}s", Duration.between(start, now()).toMillis() / 1000d);

        return builder.build();
    }

    Dataset prepareDataset(XPathDocumentExtractor extractor)
        throws IOException
    {
        try {
            VTDNav nav = extractor.vtdNav();

            String origin, created, effective;
            nav.toElement(0);
            if(nav.matchElement("AIXM-Snapshot")) {
                origin = nav.toRawString(nav.getAttrVal("origin"));
                created = nav.toRawString(nav.getAttrVal("created"));
                effective = nav.toRawString(nav.getAttrVal("effective"));
            }
            else {
                throw new IOException("Could not find 'AIXM-Snapshot' root node");
            }
            var builder = Dataset.builder();

            builder.created(now());
            builder.sourceName(sourceName);
            builder.datasetType(DATASET_TYPE);
            builder.sourceDescription(sourceDescription);
            builder.origin(origin);
            builder.created(ZonedDateTime.parse(created).toInstant());
            builder.effective(ZonedDateTime.parse(effective).toInstant());

            return builder.build();
        }
        catch (NavException e) {
            throw new IOException("Xml navigation error", e);
        }
    }

    void performAixmImport(XPathDocumentExtractor extractor, Result.ResultBuilder builder)
        throws XPathExpressionException, TransformException, ExecutionException
    {
        AtomicInteger counter = new AtomicInteger(0);

        Instant start = now();
        log.atLevel(Level.INFO).log("Starting heliport extraction");
        new HeliportsExtractor().extract(extractor).stream().peek(i -> counter.incrementAndGet()).forEach(builder::heliport);
        log.atLevel(Level.INFO).log("Extracted {} heliports in {}s", counter.getAndSet(0), Duration.between(start, now()).toMillis() / 1000d);

        start = now();
        log.atLevel(Level.INFO).log("Starting aerodrome extraction");
        new AerodromesExtractor(altitudeService).extract(extractor).stream().peek(i -> counter.incrementAndGet()).forEach(builder::aerodrome);
        log.atLevel(Level.INFO).log("Extracted {} aerodromes in {}s", counter.getAndSet(0), Duration.between(start, now()).toMillis() / 1000d);

        start = now();
        log.atLevel(Level.INFO).log("Starting airspace extraction");

        int subCount;

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.PROHIBITED), extractor, builder);
        counter.addAndGet(subCount);
        log.atLevel(Level.INFO).log("Extracted {} {} airspaces", subCount, "'P'");

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.RESTRICTED), extractor, builder);
        counter.addAndGet(subCount);
        log.atLevel(Level.INFO).log("Extracted {} {} airspaces", subCount, "'R'");

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.DANGEROUS), extractor, builder);
        counter.addAndGet(subCount);
        log.atLevel(Level.INFO).log("Extracted {} {} airspaces", subCount, "'D'");

        // Getting errors on the geometry of some of these...
//        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.NATURAL_RESERVE), extractor, builder);
//        counter.addAndGet(subCount);
//        log.atLevel(Level.INFO).log("Extracted {} {} airspaces", subCount, "'natural reserve'");

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.PARACHUTING_ZONE), extractor, builder);
        counter.addAndGet(subCount);
        log.atLevel(Level.INFO).log("Extracted {} {} airspaces", subCount, "'parachute zone'");

        log.atLevel(Level.INFO).log("Extracted {} airspaces in {}s", counter.getAndSet(0), Duration.between(start, now()).toMillis() / 1000d);
    }

    int performAirspaceExtraction(
        AirspaceExtractor airspaceExtractor, XPathDocumentExtractor extractor, Result.ResultBuilder builder
    )
        throws XPathExpressionException, TransformException
    {
        AtomicInteger counter = new AtomicInteger();
        airspaceExtractor.extract(extractor).stream().peek(i -> counter.incrementAndGet()).forEach(builder::airspace);
        return counter.get();
    }

    void performHelipadAdminUpdate(XPathDocumentExtractor extractor, Result.ResultBuilder builder)
        throws Exception
    {
        Instant start = now();
        AtomicInteger counter = new AtomicInteger();

        log.atLevel(Level.INFO).log("Extracting heliport contact info from SIA export");
        Map<String, String> admins = new SiaContactExtractor().extract(extractor);

        List<Heliport> updated = builder.heliports
            .stream()
            .map(h -> {
                if (admins.containsKey(h.name())) {
                    counter.incrementAndGet();
                    return h.withAdminAuthority(admins.get(h.name()));
                }
                else {
                    return h;
                }
            })
            .toList();

        builder.clearHeliports();
        builder.heliports(updated);

        log.atLevel(Level.INFO).log(
            "Found {} mappings and updated {} heliports out of {} in {}s",
            admins.size(), counter.get(), updated.size(), Duration.between(start, now()).toMillis() / 1000d
        );
    }
}
