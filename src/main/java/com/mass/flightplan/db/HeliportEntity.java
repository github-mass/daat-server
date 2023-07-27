package com.mass.flightplan.db;

import com.mass.flightplan.model.aixm.Heliport;
import com.mass.flightplan.model.aixm.TakeOffLandingArea;
import lombok.*;
import org.geotools.measure.Units;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.lang.Nullable;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.math.BigInteger;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Document(collection = "heliports")
@Value
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class HeliportEntity {

    public HeliportEntity(Heliport hp, DatasetEntity dse) {
        this(
            null, dse,
            hp.code(), hp.name(),
            hp.coordinates(),
            hp.elevation().to(Units.FOOT).getValue().doubleValue(),
            Optional.ofNullable(hp.geoidUndulation()).map(q -> q.to(Units.FOOT).getValue().doubleValue()).orElse(null),
            hp.magVar(), Optional.ofNullable(hp.magVarUpdated()).map(Year::getValue).orElse(null),
            hp.takeoffLandingAreas().stream().map(TakeOffLandingArea::toEntity).toList(),
            hp.adminAuthority(), hp.contactInfos()
        );
    }

    @Id
    @Getter(AccessLevel.PACKAGE)
    BigInteger id;

    @DBRef
    @NonNull
    DatasetEntity dataset;

    @NonNull
    @Field("code")
    String code;

    @NonNull
    @Field("name")
    String name;

    @NonNull
    @Field("loc")
    @GeoSpatialIndexed(name = "hp_loc_idx", type = GeoSpatialIndexType.GEO_2DSPHERE)
    Point coordinates;

    @Getter(AccessLevel.NONE)
    @Field("elevFt")
    double elevationFt;

    public Quantity<Length> elevation() {
        return Quantities.getQuantity(elevationFt, Units.FOOT);
    }

    @Getter(AccessLevel.NONE)
    @Nullable
    @Field("geoidUndFt")
    Double geoidUndulationFt;

    public Optional<Quantity<Length>> geoidUndulation() {
        return Optional.ofNullable(geoidUndulationFt).map(d -> Quantities.getQuantity(d, Units.FOOT));
    }

    @Nullable
    @Field("magVar")
    Double magVar;

    @Nullable
    @Field("magVarYear")
    Integer magVarYear;

    @Field("tlas")
    List<TlaEntity> takeoffLandingAreas;

    @Field("admin")
    @Nullable
    String adminAuthority;

    @Field("contact")
    Map<String, String> contactInfos;

}
