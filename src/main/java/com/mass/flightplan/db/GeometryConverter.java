package com.mass.flightplan.db;

import org.locationtech.jts.geom.Geometry;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.lang.NonNull;

@FunctionalInterface
public interface GeometryConverter {

    @NonNull GeoJson<?> convert(@NonNull Geometry geometry);
}
