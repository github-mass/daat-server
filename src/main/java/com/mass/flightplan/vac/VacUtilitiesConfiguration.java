package com.mass.flightplan.vac;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.lang.NonNull;

@Configuration
@Import({RestTemplateAutoConfiguration.class, RefreshAutoConfiguration.class})
public class VacUtilitiesConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "vac-atlas")
    VACAtlasProperties vacAtlasProperties(){
        return new VACAtlasProperties();
    }

    @Bean
    public VACAtlasParser vacAtlasParser(
        RestTemplateBuilder builder,
        VACAtlasProperties properties
    ){
        return new VACAtlasParser(properties, builder.build());
    }

    @Bean
    public SIAVersionFinder siaVersionFinder(
        @NonNull ConfigurableEnvironment env, @NonNull ContextRefresher refresher,
        @Value("${sia.siteplan-url}") String siaSitePlanUrl,
        @Value("${sia.auto-version.enabled:false}") boolean autoUpdateEnabled
    )
    {
        return new SIAVersionFinder(siaSitePlanUrl, autoUpdateEnabled, env, refresher);
    }


}
