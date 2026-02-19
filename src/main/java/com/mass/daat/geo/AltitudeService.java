package com.mass.daat.geo;

import org.springframework.data.geo.Point;

import javax.measure.Quantity;
import javax.measure.quantity.Length;

public interface AltitudeService {

    Quantity<Length> getAltitudeAt(Point coordinate);

}
