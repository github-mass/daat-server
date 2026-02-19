package com.mass.daat.model.aixm;

import com.mass.daat.geo.AltitudeService;
import com.mass.daat.model.Dataset;
import com.mass.daat.util.XPathDocumentExtractor;
import com.ximpleware.NavException;
import com.ximpleware.VTDNav;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.opengis.referencing.operation.TransformException;
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
        log.info("Starting DIRECTORY import from {}", source);
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
        if(siaFile == null){
            throw new IllegalStateException("Could not find SIA file in directory: " + dir);
        }

        Instant s2 = now();
        log.info("Loading AIXM data from {}", aixmFile);
        XPathDocumentExtractor extractor = XPathDocumentExtractor.from(aixmFile.toPath()).namespaceAware(false).build();
        log.info("Done loading AIXM data from {} in {}s", aixmFile, Duration.between(s2, now()).toMillis() / 1000d);

        Dataset ds = prepareDataset(extractor);

        var builder = Result.builder();
        builder.dataset(ds);
        performAixmImport(extractor, builder);

        s2 = now();
        log.info("Loading SIA data from {}", siaFile);
        extractor = XPathDocumentExtractor.from(siaFile.toPath()).namespaceAware(false).build();
        log.info("Done loading SIA data from {} in {}s", siaFile, Duration.between(s2, now()).toMillis() / 1000d);

        performHelipadAdminUpdate(extractor, builder);
        performSiaAirspacesExtraction(extractor, builder);

        log.info("DIRECTORY import complete in {}s", Duration.between(start, now()).toMillis() / 1000d);

        return builder.build();
    }

    Result performZipImport()
        throws Exception
    {
        log.info("Starting ZIP import from {}", source);
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
                        siaExtractor = XPathDocumentExtractor.from(res).namespaceAware(false).build();
                        break;
                }
            }
        }

        if(aixmExtractor == null){
            throw new IllegalStateException("Could not find AIXM file in archive: " + source);
        }
        if(siaExtractor == null){
            throw new IllegalStateException("Could not find SIA file in archive: " + source);
        }

        log.info("Done loading data from {} in {}s", source, Duration.between(s2, now()).toMillis() / 1000d);

        Dataset ds = prepareDataset(aixmExtractor);

        var builder = Result.builder();
        builder.dataset(ds);
        performAixmImport(aixmExtractor, builder);

        performHelipadAdminUpdate(siaExtractor, builder);

        performSiaAirspacesExtraction(siaExtractor, builder);

        log.info("ZIP import complete in {}s", Duration.between(start, now()).toMillis() / 1000d);

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
        log.info("Starting heliport extraction");
        new HeliportsExtractor().extract(extractor).stream().peek(i -> counter.incrementAndGet()).forEach(builder::heliport);
        log.info("Extracted {} heliports in {}s", counter.getAndSet(0), Duration.between(start, now()).toMillis() / 1000d);

        start = now();
        log.info("Starting aerodrome extraction");
        new AerodromesExtractor(altitudeService).extract(extractor).stream().peek(i -> counter.incrementAndGet()).forEach(builder::aerodrome);
        log.info("Extracted {} aerodromes in {}s", counter.getAndSet(0), Duration.between(start, now()).toMillis() / 1000d);

        start = now();
        log.info("Starting airspace extraction");

        int subCount;

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.PROHIBITED), extractor, builder);
        counter.addAndGet(subCount);
        log.info("Extracted {} {} airspaces", subCount, "'P'");

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.RESTRICTED), extractor, builder);
        counter.addAndGet(subCount);
        log.info("Extracted {} {} airspaces", subCount, "'R'");

        subCount = performAirspaceExtraction(AirspaceExtractor.forType(AirspaceType.DANGEROUS), extractor, builder);
        counter.addAndGet(subCount);
        log.info("Extracted {} {} airspaces", subCount, "'D'");

        log.info("Extracted {} airspaces in {}s", counter.getAndSet(0), Duration.between(start, now()).toMillis() / 1000d);
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

        log.info("Extracting heliport contact info from SIA export");
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

        log.info(
            "Found {} mappings and updated {} heliports out of {} in {}s",
            admins.size(), counter.get(), updated.size(), Duration.between(start, now()).toMillis() / 1000d
        );
    }

    void performSiaAirspacesExtraction(@NonNull XPathDocumentExtractor extractor, @NonNull Result.ResultBuilder builder)
        throws Exception
    {
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.NATURAL_RESERVE);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.PARACHUTING_ZONE);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.NO_LOW_OVERFLIGHT);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.RECREATIONAL_AEROBATICS);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.RECREATIONAL_MODEL_AIRCRAFT_FLIGHT);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.RECREATIONAL_WINCH_ASSISTED_FREE_FLIGHT_LAUNCH);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.RECREATIONAL_WINCH_ASSISTED_GLIDER_LAUNCH);
        extractAirspaceTypeFromSiaXml(extractor, builder, AirspaceType.RECREATIONAL_WINCH_ASSISTED_GLIDER_AND_FREE_FLIGHT_LAUNCH);
    }

    void extractAirspaceTypeFromSiaXml(@NonNull XPathDocumentExtractor extractor, @NonNull Result.ResultBuilder builder, @NonNull AirspaceType type)
        throws Exception
    {
        Instant start = now();
        AtomicInteger counter = new AtomicInteger();

        log.info("Extracting {} from SIA export", type);

        SiaZoneExtractor.forAirspaceType(type).extract(extractor).stream().peek(x -> counter.incrementAndGet()).forEach(builder::airspace);

        log.info(
            "Extracted {} {} areas in {}s",
            counter.get(), type,Duration.between(start, now()).toMillis() / 1000d
        );
    }
}
