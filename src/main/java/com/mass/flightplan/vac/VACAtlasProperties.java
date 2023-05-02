package com.mass.flightplan.vac;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;

@ConfigurationProperties(prefix = "vac-atlas")
@Data
@Accessors(fluent = false)
@RefreshScope
public class VACAtlasProperties {

    private String eAipVersion;
    private String airportListJsUrl;
    private String helipadListJsUrl;
    private String airportCardUrlTemplate;
    private String helipadCardUrlTemplate;
    private List<String> ignoredAirports;
    private List<String> ignoreRunwayExtractionErrors;

}
