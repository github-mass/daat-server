package com.mass.flightplan.db;

import com.mass.flightplan.aixm.TakeOffLandingArea;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
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

import static lombok.AccessLevel.NONE;

@Value
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class TlaEntity {

    public TlaEntity(TakeOffLandingArea tla) {
        this(
            tla.designation(), tla.coordinates(),
            Optional.ofNullable(tla.elevation()).map(q -> q.to(Units.FOOT).getValue().doubleValue()).orElse(null),
            Optional.ofNullable(tla.width()).map(q -> q.to(SI.METRE).getValue().doubleValue()).orElse(null),
            Optional.ofNullable(tla.length()).map(q -> q.to(SI.METRE).getValue().doubleValue()).orElse(null),
            tla.composition(), tla.remark()
        );
    }

    @NonNull
    @Field("code")
    String designation;

    @NonNull
    @Field("loc")
    Point coordinates;

    @Nullable
    @Field("elevFt")
    @Getter(NONE)
    Double elevationFt;

    public Optional<Quantity<Length>> elevation() {
        return Optional.ofNullable(elevationFt).map(d -> Quantities.getQuantity(d, Units.FOOT));
    }

    @Nullable
    @Field("widthM")
    @Getter(NONE)
    Double widthM;

    @Nullable
    @Field("lengthM")
    @Getter(NONE)
    Double lengthM;

    public Optional<Quantity<Length>> width() {
        return Optional.ofNullable(widthM).map(d -> Quantities.getQuantity(d, Units.METRE));
    }

    public Optional<Quantity<Length>> length() {
        return Optional.ofNullable(lengthM).map(d -> Quantities.getQuantity(d, Units.METRE));
    }

    @Nullable
    @Field("comp")
    String composition;

    @Nullable
    @Field("remark")
    String remark;

}
