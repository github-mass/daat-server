package com.mass.flightplan.model.aixm;

import com.mass.flightplan.db.AirspaceEntity;
import com.mass.flightplan.db.DatasetEntity;
import com.mass.flightplan.db.GeometryConverter;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.locationtech.jts.geom.Geometry;
import org.springframework.lang.Nullable;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.util.Set;

@Value
@Builder
public class Airspace {

    @NonNull String id;
    @NonNull String type;
    @NonNull String code;
    @Nullable String remarks;
    @Nullable String activationType;
    @Nullable String activationRemarks;

    @NonNull Quantity<Length> minFloor;
    @NonNull Quantity<Length> maxFloor;
    @NonNull Quantity<Length> minCeiling;
    @NonNull Quantity<Length> maxCeiling;

    @Singular
    Set<String> frontiers;

    @NonNull Geometry geometry;

    public static class AirspaceBuilder {

        @SuppressWarnings({"unchecked", "ConstantConditions"})
        public AirspaceBuilder adjustCeiling(Quantity<Length> ceiling){
            if(maxCeiling == null || ((Comparable<Object>)ceiling).compareTo(maxCeiling) > 0){
                maxCeiling = ceiling;
            }
            if(minCeiling == null || ((Comparable<Object>)ceiling).compareTo(minCeiling) < 0){
                minCeiling = ceiling;
            }

            return this;
        }

        @SuppressWarnings({"unchecked", "ConstantConditions"})
        public AirspaceBuilder adjustFloor(Quantity<Length> floor){
            if(maxFloor == null || ((Comparable<Object>)floor).compareTo(maxFloor) > 0){
                maxFloor = floor;
            }
            if(minFloor == null || ((Comparable<Object>)floor).compareTo(minFloor) < 0){
                minFloor = floor;
            }

            return this;
        }

    }

    public AirspaceEntity toEntity(@org.springframework.lang.NonNull DatasetEntity dataset, GeometryConverter geometryConverter){
        return new AirspaceEntity(this, dataset, geometryConverter);
    }
}
