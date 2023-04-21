package com.mass.flightplan.vac;

import com.mass.flightplan.util.IsWindowsEnvironmentCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

@Configuration
public class VacUtilitiesConfiguration {

    @Bean(autowireCandidate = false)
    @ConfigurationProperties(prefix = "vac-atlas.pdf-extraction.executor")
    ThreadPoolTaskExecutor vacPdfExtractionPoolExecutor() {
        return new ThreadPoolTaskExecutor();
    }

    @Bean
    @Scope("prototype")
    @Conditional(IsWindowsEnvironmentCondition.class)
    @ConditionalOnProperty(value = "vac-atlas.pdf-extraction.use-fs", havingValue = "true")
    public VacPdfExtractor wslOcrPdfExtractor(
        @Value("${vac-atlas.pdf-extraction.timeout}") Duration timeout,
        @Value("${vac-atlas.pdf-extraction.clean-up:true}") boolean cleanUp
    )
    {
        return new WslOcrPdfExtractor(vacPdfExtractionPoolExecutor(), timeout, cleanUp);
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnProperty(value = "vac-atlas.pdf-extraction.use-fs", havingValue = "false", matchIfMissing = true)
    public VacPdfExtractor tesseractOcrPdfExtractor(
        @Value("${vac-atlas.pdf-extraction.timeout}") Duration timeout,
        @Value("${vac-atlas.pdf-extraction.clean-up:true}") boolean cleanUp,
        @Value("${vac-atlas.pdf-extraction.ocr-dpi:400}") int ocrDpi
    )
    {
        return new TesseractOcrPdfExtractor(vacPdfExtractionPoolExecutor(), timeout, cleanUp, ocrDpi);
    }

}
