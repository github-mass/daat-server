package com.mass.daat.geo;

import lombok.experimental.UtilityClass;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;

import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@UtilityClass
public class GeoUtils {

    public static Geometry toGeometry(GeoJson<?> geojson) {
        GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
        switch (geojson.getType()) {
            case "Point" -> {
                GeoJsonPoint p = (GeoJsonPoint) geojson;
                return gf.createPoint(new Coordinate(p.getX(), p.getY()));
            }
            case "Polygon" -> {
                GeoJsonPolygon p = (GeoJsonPolygon) geojson;
                return p.getCoordinates().stream()
                        .map(ls ->
                            ls.getCoordinates()
                              .stream()
                              .map(point -> new Coordinate(point.getX(), point.getY()))
                        )
                        .map(stream -> gf.createLinearRing(stream.toArray(Coordinate[]::new)))
                        .collect(Collectors.collectingAndThen(toList(), l -> gf.createPolygon(l.get(0), l.stream().skip(1).toArray(LinearRing[]::new))));
            }
            case "MultiPolygon" -> {
                GeoJsonMultiPolygon p = (GeoJsonMultiPolygon) geojson;
                return p.getCoordinates().stream()
                        .map(GeoUtils::toGeometry)
                        .collect(Collectors.collectingAndThen(toList(), gf::buildGeometry));
            }
            default -> throw new IllegalArgumentException("Unexpected GeoJSON: " + geojson);
        }
    }

}
