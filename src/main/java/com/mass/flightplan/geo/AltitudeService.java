package com.mass.flightplan.geo;

import org.springframework.data.geo.Point;

import javax.measure.quantity.Length;

public interface AltitudeService {

    Length getAltitudeAt(Point coordinate);

}
