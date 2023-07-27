package com.mass.flightplan.model.aixm;

import com.mass.flightplan.db.RunwayEntity;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import javax.measure.Quantity;
import javax.measure.quantity.Length;

@Value
@Builder
public class Runway {

    @NonNull
    String designation;

    @Nullable
    Point coordinates;

    @NonNull
    Quantity<Length> length;

    @NonNull
    Quantity<Length> width;

    @Nullable
    String surface;

    @Nullable
    Quantity<Length> minElevation;

    @Nullable
    Quantity<Length> maxElevation;

    boolean paved;

    @Nullable
    Double trueBearing;

    @Nullable
    Double magBearing;

    public RunwayEntity toEntity(){
        return new RunwayEntity(this);
    }
}
