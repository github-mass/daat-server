package com.mass.daat;

import com.mass.daat.db.AixmDbImporter;
import com.mass.daat.db.DatasetEntity;
import com.mass.daat.db.DatasetRepository;
import com.mass.daat.geo.AltitudeService;
import com.mass.daat.model.aixm.AixmImporter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class AixmUpdateService {

    private final @NonNull AixmProperties properties;
    private final @NonNull AltitudeService altitudeService;
    private final @NonNull AixmDbImporter dbImporter;

    private final @NonNull DatasetRepository dataSetRepo;

    @Scheduled(
        initialDelayString = "${aixm.update.initial-delay}",
        fixedDelayString = "${aixm.update.fixed-delay}",
        timeUnit = TimeUnit.MINUTES
    )
    public void checkForUpdate(){
        if(!properties.getUpdate().isEnabled()){
            return ;
        }

        try {
            log.info("Checking for updated AIXM sources");

            List<AixmProperties.AixmImportSource> todo = sourcesToBeImported();

            log.info("Found sources to import: {}", todo);

            performImport(todo);
        }
        catch (Throwable t) {
            log.error("Unexpected error in AIXM update service", t);
        }
    }

    private List<AixmProperties.AixmImportSource> sourcesToBeImported() {
        List<AixmProperties.AixmImportSource> sources = properties.getImport().getSources();
        log.debug("Found sources: {}", sources);

        if(sources == null || sources.isEmpty()){
            return List.of();
        }

        List<AixmProperties.AixmImportSource> copy = new ArrayList<>(sources);

        Set<String> existing = new HashSet<>();
        for (DatasetEntity dse : dataSetRepo.findAllAixm()) {
            if(dse.deprecated() != Boolean.TRUE) {
                existing.add(dse.sourceName());
            }
        }

        copy.removeIf(s -> existing.contains(s.getUri()));

        return copy;
    }

    void performImport(List<AixmProperties.AixmImportSource> sources) {
        for (AixmProperties.AixmImportSource source : sources) {
            log.info("Trying to import: {}", source);

            Resource res;
            try {
                URI uri = URI.create(source.getUri());
                res = new UrlResource(uri);
            }
            catch (IllegalArgumentException | MalformedURLException x) {
                log.error("Invalid data source: {}, not a valid URI", source, x);
                continue;
            }

            try {
                performImport(source, res);
            }
            catch (Exception x) {
                log.error("Import failed for {}", source, x);
            }
        }
    }

    void performImport(AixmProperties.AixmImportSource source, Resource resource)
        throws Exception
    {
        AixmImporter imp = AixmImporter.builder()
                                       .sourceName(source.getUri())
                                       .sourceDescription(source.getDescription())
                                       .altitudeService(altitudeService)
                                       .source(resource)
                                       .build();

        var result = imp.perform();
        dbImporter.importResult(result);
    }
}
