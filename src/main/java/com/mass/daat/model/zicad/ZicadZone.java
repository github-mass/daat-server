package com.mass.daat.model.zicad;

import com.mass.daat.db.DatasetEntity;
import com.mass.daat.db.ZicadEntity;
import com.mass.daat.util.GeometryConverter;
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
