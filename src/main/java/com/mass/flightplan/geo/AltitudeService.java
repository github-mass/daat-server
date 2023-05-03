package com.mass.flightplan.geo;

import org.locationtech.jts.geom.Coordinate;

import javax.measure.quantity.Length;

public interface AltitudeService {

    Length getAltitudeAt(Coordinate coordinate);

}
