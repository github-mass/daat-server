package com.mass.flightplan.geo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "altitude-service")
@Data
@Accessors(fluent = false)
@Validated
public class AltitudeServiceProperties {

    private String type;

    @NestedConfigurationProperty
    private IgnServiceProperties ign = new IgnServiceProperties();

    @Data
    @Accessors(fluent = false)
    @Validated
    @NoArgsConstructor
    public static class IgnServiceProperties {
        @NonNull
        private String restServiceUrl;
        @NonNull
        private String dataResourceId;
    }
}
