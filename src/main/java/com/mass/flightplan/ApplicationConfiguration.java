package com.mass.flightplan;

import com.mass.flightplan.db.AixmDbImporter;
import com.mass.flightplan.db.DatasetRepository;
import com.mass.flightplan.geo.AltitudeService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.NonNull;

@Configuration
public class ApplicationConfiguration {

    @Bean
    @RefreshScope
    public AixmProperties aixmProperties(){
        return new AixmProperties();
    }

    @Bean
    public AixmDbImporter aixmDbImporter(@NonNull MongoTemplate mongo){
        return new AixmDbImporter(mongo);
    }

    @Bean
    public AixmUpdateService aixmUpdateService(
        @NonNull AixmProperties properties,
        @NonNull AltitudeService altitudeService,
        @NonNull AixmDbImporter dbImporter,
        @NonNull DatasetRepository dataSetRepo
    ){
        return new AixmUpdateService(properties, altitudeService, dbImporter, dataSetRepo);
    }

}
