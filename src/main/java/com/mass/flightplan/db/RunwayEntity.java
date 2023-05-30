package com.mass.flightplan.db;

import com.mass.flightplan.aixm.Runway;
import lombok.*;
import org.geotools.measure.Units;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.lang.Nullable;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.util.Optional;

@Value
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class RunwayEntity {

    public RunwayEntity(Runway rwy){
        this(
            rwy.designation(), rwy.coordinates(),
            rwy.length().to(SI.METRE).getValue().doubleValue(), rwy.width().to(SI.METRE).getValue().doubleValue(),
            rwy.surface(),
            Optional.ofNullable(rwy.minElevation()).map(a -> a.to(Units.FOOT).getValue().doubleValue()).orElse(null),
            Optional.ofNullable(rwy.maxElevation()).map(a -> a.to(Units.FOOT).getValue().doubleValue()).orElse(null),
            rwy.paved(),
            rwy.trueBearing(), rwy.magBearing()
        );
    }

    @NonNull
    @Field("code")
    String designation;

    @Field("loc")
    @Nullable
    Point coordinates;

    @Getter(AccessLevel.NONE)
    @Field("lengthM")
    double lengthMetres;

    @Getter(AccessLevel.NONE)
    @Field("widthM")
    double widthMetres;

    public Quantity<Length> length(){
        return Quantities.getQuantity(lengthMetres, SI.METRE);
    }

    public Quantity<Length> width(){
        return Quantities.getQuantity(widthMetres, SI.METRE);
    }

    @Field("comp")
    @Nullable
    String composition;

    @Nullable
    @Getter(AccessLevel.NONE)
    @Field("minElevFt")
    Double minElevationFt;

    @Nullable
    @Getter(AccessLevel.NONE)
    @Field("maxElevFt")
    Double maxElevationFt;

    public Optional<Quantity<Length>> minElevation(){
        return Optional.ofNullable(minElevationFt).map(d -> Quantities.getQuantity(d, Units.FOOT));
    }

    public Optional<Quantity<Length>> maxElevation(){
        return Optional.ofNullable(maxElevationFt).map(d -> Quantities.getQuantity(d, Units.FOOT));
    }

    @Field("paved")
    boolean paved;

    @Nullable
    @Field("trueBrg")
    Double trueBearing;

    @Nullable
    @Field("magBrg")
    Double magBearing;

}
