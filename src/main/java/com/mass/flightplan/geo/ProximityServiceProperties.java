package com.mass.flightplan.geo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = false)
public class ProximityServiceProperties {

    private double helipadMaxDistanceKM = 10;

    private double airportMaxDistanceKM = 20;

    private double airspaceMaxDistanceKM = 2; // max distance the drone can legally fly from the mission point.

    private double zicadMaxDistanceKM = 2;

}
