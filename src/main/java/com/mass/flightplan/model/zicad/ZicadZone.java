package com.mass.flightplan.model.zicad;

import com.mass.flightplan.db.DatasetEntity;
import com.mass.flightplan.db.ZicadEntity;
import com.mass.flightplan.util.GeometryConverter;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;

@Value
@Builder
public class ZicadZone {
    @NonNull String commune;
    @NonNull String ministry;
    @NonNull Instant effective;
    @NonNull String areaId;
    @NonNull String siteName;
    @NonNull Geometry geometry;

    public ZicadEntity toEntity(DatasetEntity de, GeometryConverter conv){
        return new ZicadEntity(this, de, conv);
    }
}
