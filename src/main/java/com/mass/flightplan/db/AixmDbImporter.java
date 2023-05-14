package com.mass.flightplan.db;

import com.mass.flightplan.aixm.Aerodrome;
import com.mass.flightplan.aixm.Airspace;
import com.mass.flightplan.aixm.AixmImporter;
import com.mass.flightplan.aixm.Heliport;
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
public class AixmDbImporter {

    private final @lombok.NonNull MongoTemplate mongo;

    public void importResult(@NonNull AixmImporter.Result result){
        log.info("Storing dataset to DB: {}", result.dataset());
        DatasetEntity dse = result.dataset().toEntity();
        dse = mongo.insert(dse);

        try{
            tryImportResult(dse, result);
        }
        catch (Exception x) {
            log.error("Storing of dataset {} failed; rolling back.", dse, x);
            rollback(dse);
        }
    }

    private void rollback(DatasetEntity dse){
        if(dse.id() == null){
            return ;
        }

        var result = mongo.remove(query(where("dataset").is(dse)), HeliportEntity.class);
        log.info("Rolled back {} heliport entries", result.getDeletedCount());

        result = mongo.remove(query(where("dataset").is(dse)), AerodromeEntity.class);
        log.info("Rolled back {} aerodrome entries", result.getDeletedCount());

        result = mongo.remove(query(where("dataset").is(dse)), AirspaceEntity.class);
        log.info("Rolled back {} airspace entries", result.getDeletedCount());

        result = mongo.remove(dse);
        log.info("Removed {} dataset entry.", result.getDeletedCount());
    }

    private void tryImportResult(DatasetEntity dse, @NonNull AixmImporter.Result result){
        Instant start = now();

        for(Aerodrome ae: result.aerodromes()){
            //if there's a CTR, take care of that first
            AerodromeEntity aee = ae.toEntity(dse);

            AirspaceEntity ctr = aee.ctr();
            if(ctr != null){
                aee = aee.withCtr(mongo.insert(ctr));
            }

            mongo.insert(aee);
        }

        for(Heliport hp: result.heliports()){
            HeliportEntity hee = hp.toEntity(dse);
            mongo.insert(hee);
        }

        for(Airspace as: result.airspaces()){
            AirspaceEntity ase = as.toEntity(dse);
            mongo.insert(ase);
        }

        dse.imported(now());
        mongo.save(dse);

        log.info("Done storing after {}s", between(start, now()).toMillis() / 1000d);
    }
}
