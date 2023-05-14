package com.mass.flightplan.aixm;

import com.mass.flightplan.db.AerodromeEntity;
import com.mass.flightplan.db.DatasetEntity;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

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
    Length elevation;

    @Nullable
    Length geoidUndulation;

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

    public AerodromeEntity toEntity(@org.springframework.lang.NonNull DatasetEntity dse){
        return new AerodromeEntity(this, dse);
    }
}
