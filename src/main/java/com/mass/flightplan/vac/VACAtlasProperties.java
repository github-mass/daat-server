package com.mass.flightplan.vac;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@Accessors(fluent = false)
@RefreshScope
public class VACAtlasProperties {

    private String eAipVersion;
    private String airportListJsUrl;
    private String helipadListJsUrl;
    private String airportCardUrlTemplate;
    private String helipadCardUrlTemplate;

}
