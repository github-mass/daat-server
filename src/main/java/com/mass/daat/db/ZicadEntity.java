package com.mass.daat.db;

import com.mass.daat.model.zicad.ZicadZone;
import com.mass.daat.util.GeometryConverter;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigInteger;
import java.time.Instant;

@Document(collection = "zicad")
@Data
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class ZicadEntity {

    public ZicadEntity(ZicadZone zone, DatasetEntity dataset, GeometryConverter conv){
        this(
            null, dataset,
            zone.commune(), zone.ministry(), zone.effective(), zone.areaId(), zone.siteName(),
            conv.convert(zone.geometry())
        );
    }

    @Id
    @Setter(AccessLevel.NONE)
    BigInteger id;

    @DBRef
    @NonNull
    DatasetEntity dataset;

    @NonNull
    String commune;

    @NonNull
    String ministry;

    @NonNull
    Instant effective;

    @NonNull
    @Indexed(name = "zicad_area_idx")
    String areaId;

    @NonNull
    String siteName;

    @NonNull
    @GeoSpatialIndexed(name = "zicad_geom_idx", type = GeoSpatialIndexType.GEO_2DSPHERE)
    GeoJson<?> geometry;

}
