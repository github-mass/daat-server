package com.mass.flightplan;

import com.mass.flightplan.db.DatasetEntity;
import com.mass.flightplan.db.DatasetRepository;
import com.mass.flightplan.db.ZicadDbImporter;
import com.mass.flightplan.model.zicad.ZicadImporter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class ZicadUpdateService {

    private final @NonNull ZicadProperties properties;
    private final @NonNull ZicadDbImporter dbImporter;

    private final @NonNull DatasetRepository dataSetRepo;

    @Scheduled(
        initialDelayString = "${zicad.update.initial-delay}",
        fixedDelayString = "${zicad.update.fixed-delay}",
        timeUnit = TimeUnit.MINUTES
    )
    public void checkForUpdate() {
        if (!properties.getUpdate().isEnabled()) {
            return;
        }

        try {
            log.info("Checking for updated ZICAD sources");

            List<ZicadProperties.ZicadImportSource> todo = sourcesToBeImported();

            log.info("Found sources to import: {}", todo);

            performImport(todo);
        }
        catch (Throwable t) {
            log.error("Unexpected error in ZICAD update service", t);
        }
    }

    private List<ZicadProperties.ZicadImportSource> sourcesToBeImported() {
        List<ZicadProperties.ZicadImportSource> sources = properties.getImport().getSources();
        log.debug("Found sources: {}", sources);

        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        List<ZicadProperties.ZicadImportSource> copy = new ArrayList<>(sources);

        Set<String> existing = new HashSet<>();
        for (DatasetEntity dse : dataSetRepo.findAllZicad()) {
            if (dse.deprecated() != Boolean.TRUE) {
                existing.add(dse.sourceName());
            }
        }

        copy.removeIf(s -> existing.contains(s.getUri()));

        return copy;
    }

    void performImport(List<ZicadProperties.ZicadImportSource> sources) {
        for (ZicadProperties.ZicadImportSource source : sources) {
            log.info("Trying to import: {}", source);

            URL res;
            try {
                URI uri = URI.create(source.getUri());
                res = uri.toURL();
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

    void performImport(ZicadProperties.ZicadImportSource source, URL resource)
        throws Exception
    {
        ZicadImporter imp = ZicadImporter.builder()
                                         .sourceName(source.getUri())
                                         .sourceDescription(source.getDescription())
                                         .source(resource)
                                         .build();

        var result = imp.perform();
        dbImporter.importResult(result);
    }
}
