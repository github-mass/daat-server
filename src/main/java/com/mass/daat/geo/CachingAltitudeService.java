package com.mass.daat.geo;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.geo.Point;

import javax.measure.Quantity;
import javax.measure.quantity.Length;

@RequiredArgsConstructor
public class CachingAltitudeService
    implements AltitudeService
{
    private final AltitudeService delegate;

    @Override
    @Cacheable("altitude")
    public Quantity<Length> getAltitudeAt(Point coordinate) {
        return delegate.getAltitudeAt(coordinate);
    }
}
