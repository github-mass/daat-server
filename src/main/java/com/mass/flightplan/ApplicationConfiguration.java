package com.mass.flightplan;

import com.mass.flightplan.vac.VACAtlasParser;
import com.mass.flightplan.vac.VACAtlasProperties;
import com.mass.flightplan.vac.VAChartParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;

@Configuration
public class ApplicationConfiguration {

    @Bean
    @ConditionalOnProperty(value = "vac-atlas.update.enabled", havingValue = "true")
    public VacUpdateService vacUpdateService(
        VACAtlasProperties atlasProperties, VACAtlasParser atlasParser, VAChartParser chartParser,
        MongoTemplate mongoTemplate,
        @Value("${vac-atlas.update.refresh-when-older-than:1d}") Duration refreshWhenOlderThan,
        @Value("${vac-atlas.update.max-errors:3}") int maxErrors
    ){
        return new VacUpdateService(refreshWhenOlderThan, maxErrors, atlasProperties, atlasParser, chartParser, mongoTemplate);
    }

}
