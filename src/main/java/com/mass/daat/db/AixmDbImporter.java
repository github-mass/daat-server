package com.mass.daat.db;

import com.mass.daat.geo.GeoUtils;
import com.mass.daat.model.aixm.Aerodrome;
import com.mass.daat.model.aixm.Airspace;
import com.mass.daat.model.aixm.AixmImporter;
import com.mass.daat.model.aixm.Heliport;
import com.mass.daat.util.GeometryConverter;
import com.mass.daat.util.GeometryFixer;
import com.mongodb.MongoWriteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final @lombok.NonNull GeometryConverter geometryConverter;
    private final @lombok.NonNull GeometryFixer geometryFixer;

    public DatasetEntity importResult(@NonNull AixmImporter.Result result){
        log.info("Storing dataset to DB: {}", result.dataset());
        DatasetEntity dse = result.dataset().toEntity();
        dse = mongo.insert(dse);

        try{
            tryImportResult(dse, result);
        }
        catch (Exception x) {
            log.error("Storing of dataset {} failed; rolling back.", dse, x);
            purge(dse);
        }

        return dse;
    }

    public void purge(DatasetEntity dse){
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
            AerodromeEntity aee = ae.toEntity(dse, geometryConverter);

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
            AirspaceEntity ase = as.toEntity(dse, geometryConverter);
            try {
                mongo.insert(ase);
            }
            catch (DataIntegrityViolationException divex) {
                if(maybeInvalidGeometryError(divex)){
                    //trying fixing geometry and insert again
                    //We might consider marking the object to the effect that we've had to alter the geometry and that results might be inaccurate...
                    ase = tryFixAirspaceGeometry(ase);
                    try {
                        mongo.insert(ase);
                        log.info("Geometry fixed successfully: {}", ase);
                    }
                    catch (Exception x) {
                        log.warn("Fixing geometry failed for {}", ase, x);
                        throw divex; //re-throw original exception
                    }
                }
            }
        }

        dse.imported(now());
        mongo.save(dse);

        log.info("Done storing after {}s", between(start, now()).toMillis() / 1000d);
    }

    static boolean maybeInvalidGeometryError(Throwable t){
        if(t instanceof DataIntegrityViolationException divex){
            if(divex.getCause() instanceof MongoWriteException mwex){
                return mwex.getError().getCode() == 16755 || mwex.getError().getMessage().toLowerCase().contains("can't extract geo keys");
            }
        }

        return false;
    }

    AirspaceEntity tryFixAirspaceGeometry(AirspaceEntity ae){
        log.debug("Attempting to fix geometry: {}", ae);
        try {
            Geometry geom = GeoUtils.toGeometry(ae.geometry());
            geom = geometryFixer.fix(geom);
            return ae.geometry(geometryConverter.convert(geom));
        }
        catch (Exception x) {
            log.warn("Failed to fix geometry for {}", ae, x);
            return ae;
        }
    }
}
