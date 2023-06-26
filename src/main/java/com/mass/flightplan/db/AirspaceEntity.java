package com.mass.flightplan.db;

import com.mass.flightplan.aixm.Airspace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.lang.Nullable;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Document(collection = "airspaces")
@Data
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class AirspaceEntity {

    public AirspaceEntity(Airspace as, DatasetEntity dataset) {
        this(
            null, dataset,
            as.type(), as.name(), as.remarks(),
            as.minFloor().toString(), as.maxFloor().toString(),
            as.minCeiling().toString(), as.maxCeiling().toString(),
            as.frontiers(),
            geometryToGeoJson(as)
        );
    }

    static GeoJson<?> geometryToGeoJson(Airspace as) {
        Geometry g = as.geometry();
        if (g instanceof MultiPolygon) {
            return mPolygonToGeoJson(g);
        }
        if (g instanceof Polygon) {
            return polygonToGeoJson(g);
        }
        if(g instanceof LineString){
            //use a point
            return pseudoPointToGeoJson(as, (LineString) g);
        }

        throw new IllegalArgumentException("Unexpected geometry type for airspace %s: %s".formatted(as.id(), g.toText()));
    }

    static GeoJsonPolygon polygonToGeoJson(Geometry g) {
        List<Point> points = Arrays.stream(g.getCoordinates())
                                   .map(c -> new Point(c.x, c.y))
                                   .toList();

        return new GeoJsonPolygon(points);
    }

    static GeoJsonMultiPolygon mPolygonToGeoJson(Geometry g) {
        var l = IntStream.range(0, g.getNumGeometries())
                         .mapToObj(g::getGeometryN)
                         .map(AirspaceEntity::polygonToGeoJson)
                         .toList();
        return new GeoJsonMultiPolygon(l);
    }

    static GeoJsonPoint pseudoPointToGeoJson(Airspace as, LineString g){
        if(!g.getStartPoint().equalsExact(g.getEndPoint())){
            throw new IllegalArgumentException(
                ("Attempted to treat LineString as pseudo-point for airspace %s; " +
                    "expected start- and endpoint to match, got got: %s").formatted(as.id(), g));
        }

        return new GeoJsonPoint(g.getStartPoint().getX(), g.getStartPoint().getY());
    }


    @Id
    BigInteger id;

    @DBRef
    @NonNull
    DatasetEntity dataset;

    @Field("type")
    @NonNull
    String type;

    @Field("code")
    @NonNull
    String code;

    @Field("remark")
    @Nullable
    String remark;

    @Field("minFloor")
    String minFloor;

    @Field("maxFloor")
    String maxFloor;

    @Field("minCeiling")
    String minCeiling;

    @Field("maxCeiling")
    String maxCeiling;

    @Nullable
    Set<String> frontiers;

    @NonNull
    @GeoSpatialIndexed(name = "aes_geom_idx", type = GeoSpatialIndexType.GEO_2DSPHERE)
    GeoJson<?> geometry;

}
