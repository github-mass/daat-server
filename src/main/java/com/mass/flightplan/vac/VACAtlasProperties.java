package com.mass.flightplan.vac;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vac-atlas")
@Data
@Accessors(fluent = false)
public class VACAtlasProperties {

    private String airportListJsUrl;
    private String heliportListJsUrl;
    private String airportCardUrlTemplate;
    private String heliportCardUrlTemplate;

}
