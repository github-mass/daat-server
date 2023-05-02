package com.mass.flightplan.vac;

import com.mass.flightplan.util.IsUnixEnvironmentCondition;
import com.mass.flightplan.util.IsWindowsEnvironmentCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class VacUtilitiesConfiguration {

    @Bean(autowireCandidate = false)
    @ConfigurationProperties(prefix = "vac-atlas.pdf-extraction.executor")
    ThreadPoolTaskExecutor vacPdfExtractionPoolExecutor() {
        return new ThreadPoolTaskExecutor();
    }

    @Bean
    @ConditionalOnProperty(value = "vac-atlas.pdf-extraction.type", havingValue = "pdftotext")
    @Conditional(IsUnixEnvironmentCondition.class)
    public VacPdfExtractor pdftotextOcrPdfExtractor(
        @Value("${vac-atlas.pdf-extraction.timeout}") Duration timeout
    )
    {
        return new PdftotextExtractor(vacPdfExtractionPoolExecutor(), timeout);
    }

    @Bean
    @ConditionalOnProperty(value = "vac-atlas.pdf-extraction.type", havingValue = "pdftotext")
    @Conditional(IsWindowsEnvironmentCondition.class)
    public VacPdfExtractor wslPdftotextOcrPdfExtractor(
        @Value("${vac-atlas.pdf-extraction.timeout}") Duration timeout
    )
    {
        return new PdftotextExtractor(vacPdfExtractionPoolExecutor(), timeout){
            @Override
            protected Process createProcess(List<String> cmd)
                throws IOException
            {
                var alt = new ArrayList<>(cmd);
                alt.add(0, "wsl.exe");
                alt.add(1, "-e");

                return super.createProcess(alt);
            }
        };
    }

    @Bean
    public VAChartParser blockVAChartParser(
        @NonNull VacPdfExtractor pdfExtractor, @NonNull VACAtlasProperties atlasProperties,
        @Value("${vac-atlas.pdf-extraction.keep-files:false}") boolean keepFiles
    )
    {
        return new BlockVAChartParser(pdfExtractor, atlasProperties, keepFiles);
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
