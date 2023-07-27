package com.mass.flightplan.db;

import com.mass.flightplan.model.aixm.Airspace;
import com.mass.flightplan.util.GeometryConverter;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.lang.Nullable;

import java.math.BigInteger;
import java.util.Set;

@Document(collection = "airspaces")
@Data
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class AirspaceEntity {

    public AirspaceEntity(Airspace as, DatasetEntity dataset, GeometryConverter converter) {
        this(
            null, dataset,
            as.type(), as.code(), as.name(), as.remarks(),
            as.activationType(), as.activationRemarks(),
            as.minFloor().toString(), as.maxFloor().toString(),
            as.minCeiling().toString(), as.maxCeiling().toString(),
            as.frontiers(),
            converter.convert(as.geometry())
        );
    }

    @Id
    @Setter(AccessLevel.NONE)
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

    @Field("name")
    @Nullable
    String name;

    @Field("remark")
    @Nullable
    String remark;

    @Field("activationType")
    @Nullable
    String activationType;

    @Field("activationRemark")
    @Nullable
    String activationRemark;


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
