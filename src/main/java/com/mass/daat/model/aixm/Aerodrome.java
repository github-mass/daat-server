package com.mass.daat.model.aixm;

import com.mass.daat.db.AerodromeEntity;
import com.mass.daat.db.DatasetEntity;
import com.mass.daat.util.GeometryConverter;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.time.Year;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class Aerodrome {

    @NonNull String id;

    @NonNull String code;

    @NonNull String name;

    @Nullable
    String servedCity;

    @Nullable
    String siteDescription;

    @Nullable
    String adminAuthority;

    @NonNull
    Point coordinates;

    @NonNull
    Quantity<Length> elevation;

    @Nullable
    Quantity<Length> geoidUndulation;

    @Nullable
    Double magVar;

    @Nullable
    Year magVarUpdated;

    @Singular
    List<Runway> runways;

    @Singular
    Map<String, String> contactInfos;

    @Nullable
    Airspace ctr;

    public AerodromeEntity toEntity(@org.springframework.lang.NonNull DatasetEntity dse, GeometryConverter geometryConverter){
        return new AerodromeEntity(this, dse, geometryConverter);
    }
}
