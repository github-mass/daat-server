package com.mass.flightplan;

import com.mass.flightplan.db.AirportEntity;
import com.mass.flightplan.db.HelipadEntity;
import com.mass.flightplan.db.VacDataEntity;
import com.mass.flightplan.vac.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.between;
import static java.time.Instant.now;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@RequiredArgsConstructor
@Log4j2
public class VacUpdateService {

    @NonNull
    private final Duration refreshWhenOlderThan;
    private final int maxErrors;

    @NonNull
    private final VACAtlasProperties atlasProperties;
    @NonNull
    private final VACAtlasParser atlasParser;
    @NonNull
    private final VAChartParser chartParser;

    @NonNull
    private final MongoTemplate mt;


    @EventListener(ApplicationReadyEvent.class) //FIXME for testing only
    //cf org.springframework.scheduling.support.CronExpression
    @Scheduled(cron = "${vac-atlas.update.cron}")
    public void updateVacInfo()
        throws InterruptedException
    {
        log.info("Running VAC update...");
        final Instant start = now();

        try {
            updateHelipadCharts();
        }
        catch (IOException e) {
            log.warn("HVAC update failed: ", e);
        }
        catch (ExecutionException e) {
            log.warn("HVAC update failed: ", e.getCause());
        }

        try {
            updateAirportCharts();
        }
        catch (IOException e) {
            log.warn("VAC update failed: ", e);
        }
        catch (ExecutionException e) {
            log.warn("VAC update failed: ", e.getCause());
        }

        log.info("VAC update completed in {}", between(start, now()));
    }

    private void updateAirportCharts()
        throws IOException, ExecutionException, InterruptedException
    {
        Map<String, String> airports = atlasParser.fetchAirportMap();
        Set<String> toUpdate = new TreeSet<>(airports.keySet()); //use treeset to have a quick alphanum sorting

        //TODO: Delete VACs that aren't in the new list?

        mt.findAll(AirportEntity.class).forEach(
            vac -> {
                if (
                    !vac.forceUpdate()
                        && atlasProperties.getEAipVersion().equals(vac.eAipVersion())
                        && between(vac.updated(), Instant.now()).compareTo(refreshWhenOlderThan) < 0
                ) {
                    toUpdate.remove(vac.code());
                }
            }
        );

        log.info("Found {} VAC entries to update: {}", toUpdate.size(), toUpdate);

        final Instant start = now();
        int errors = 0;
        for (String vacCode : toUpdate) {
            try {
                Resource vac = atlasParser.fetchAirportVacCard(vacCode);
                AirportInfo info = chartParser.parseAirportInfo(vac, vacCode, airports.get(vacCode));

                log.debug("VAC fetching and parsing successful for {} ({}); storing...", vacCode, airports.get(vacCode));

                AirportEntity ae = new AirportEntity(info);
                mt.save(ae);

                VacDataEntity de = VacDataEntity.builder()
                                                .code(vacCode)
                                                .type(VacDataEntity.Type.VAC)
                                                .url(vac.getURL().toExternalForm())
                                                .data(vac.getContentAsByteArray())
                                                .build();

                mt.update(VacDataEntity.class)
                    .matching(where("code").is(de.code()).and("type").is(de.type()))
                    .replaceWith(de)
                    .withOptions(FindAndReplaceOptions.options().upsert())
                    .findAndReplaceValue()
                ;
            }
            catch (IOException | RuntimeException x) {
                log.warn("VAC update failed for {}:", vacCode, x);
                if (++errors > maxErrors) {
                    log.warn("MAX_ERRORS ({}) reached; cancelling task.", maxErrors);
                    throw x;
                }
            }
        }

        log.info("Updated {} VAC entries in {} ({} errors)", toUpdate.size(), between(start, now()), errors);
    }

    private void updateHelipadCharts()
        throws IOException, ExecutionException, InterruptedException
    {
        Map<String, String> helipads = atlasParser.fetchHelipadMap();
        Set<String> toUpdate = new TreeSet<>(helipads.keySet()); //use treeset to have a quick alphanum sorting

        //TODO: Delete VACs that aren't in the new list?

        mt.findAll(HelipadEntity.class).forEach(
            hvac -> {
                if (
                    !hvac.forceUpdate()
                        && atlasProperties.getEAipVersion().equals(hvac.eAipVersion())
                        && between(hvac.updated(), Instant.now()).compareTo(refreshWhenOlderThan) < 0
                ) {
                    toUpdate.remove(hvac.code());
                }
            }
        );

        log.info("Found {} HVAC entries to update: {}", toUpdate.size(), toUpdate);

        final Instant start = now();
        int errors = 0;
        for (String hvacCode : toUpdate) {
            try {
                Resource hvac = atlasParser.fetchHelipadVacCard(hvacCode);
                HelipadInfo info = chartParser.parseHeliportInfo(hvac, hvacCode, helipads.get(hvacCode));

                log.debug("HVAC fetching and parsing successful for {} ({}); storing...", hvacCode, helipads.get(hvacCode));

                HelipadEntity he = new HelipadEntity(info);
                mt.save(he);

                VacDataEntity de = VacDataEntity.builder()
                                                .code(hvacCode)
                                                .type(VacDataEntity.Type.HVAC)
                                                .url(hvac.getURL().toExternalForm())
                                                .data(hvac.getContentAsByteArray())
                                                .build();

                mt.update(VacDataEntity.class)
                  .matching(where("code").is(de.code()).and("type").is(de.type()))
                  .replaceWith(de)
                  .withOptions(FindAndReplaceOptions.options().upsert())
                  .findAndReplaceValue()
                ;
            }
            catch (IOException | RuntimeException x) {
                log.warn("HVAC update failed for {}:", hvacCode, x);
                if (++errors > maxErrors) {
                    log.warn("MAX_ERRORS ({}) reached; cancelling task.", maxErrors);
                    throw x;
                }
            }
        }

        log.info("Updated {} HVAC entries in {} ({} errors)", toUpdate.size(), between(start, now()), errors);
    }
}
