package com.mass.flightplan.db;

import com.mass.flightplan.model.zicad.ZicadImporter;
import com.mass.flightplan.model.zicad.ZicadZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.NonNull;

import java.time.Instant;

import static java.time.Duration.between;
import static java.time.Instant.now;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Slf4j
public class ZicadDbImporter {

    private final @lombok.NonNull MongoTemplate mongo;
    private final @lombok.NonNull GeometryConverter geometryConverter;

    public void importResult(@NonNull ZicadImporter.Result result) {
        log.info("Storing dataset to DB: {}", result.dataset());
        DatasetEntity dse = result.dataset().toEntity();
        dse = mongo.insert(dse);

        try {
            tryImportResult(dse, result);
        }
        catch (Exception x) {
            log.error("Storing of dataset {} failed; rolling back.", dse, x);
            rollback(dse);
        }
    }

    private void rollback(DatasetEntity dse) {
        if (dse.id() == null) {
            return;
        }

        var result = mongo.remove(query(where("dataset").is(dse)), ZicadEntity.class);
        log.info("Rolled back {} ZICAD entries", result.getDeletedCount());

        result = mongo.remove(dse);
        log.info("Removed {} dataset entry.", result.getDeletedCount());
    }

    private void tryImportResult(DatasetEntity dse, @NonNull ZicadImporter.Result result) {
        Instant start = now();

        for (ZicadZone zone : result.zones()) {
            ZicadEntity ze = zone.toEntity(dse, geometryConverter);
            mongo.insert(ze);
        }

        dse.imported(now());
        mongo.save(dse);

        log.info("Done storing after {}s", between(start, now()).toMillis() / 1000d);
    }
}
