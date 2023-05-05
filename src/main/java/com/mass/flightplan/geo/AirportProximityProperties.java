package com.mass.flightplan.geo;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proximity-service")
@Data
@Accessors(fluent = false)
public class AirportProximityProperties {

    private double helipadMaxDistanceKM = 10;

    private double airportMaxDistanceKM = 20;

}
