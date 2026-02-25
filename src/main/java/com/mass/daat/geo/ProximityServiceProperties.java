package com.mass.daat.geo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = false)
public class ProximityServiceProperties {

    private double helipadMaxDistanceKm = 10;

    private double airportMaxDistanceKm = 20;

    private double airspaceMaxDistanceKm = 2; // max distance the drone can legally fly from the mission point.

    private double zicadMaxDistanceKm = 2;

}
