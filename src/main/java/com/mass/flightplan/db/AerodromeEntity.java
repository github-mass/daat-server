package com.mass.flightplan.db;

import com.mass.flightplan.aixm.Aerodrome;
import com.mass.flightplan.aixm.Runway;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.lang.Nullable;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.math.BigInteger;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.geotools.measure.Units.FOOT;
import static tech.units.indriya.quantity.Quantities.getQuantity;

@Document(collection = "aerodromes")
@Value
@AllArgsConstructor(onConstructor_ = {@PersistenceCreator})
public class AerodromeEntity {

    public AerodromeEntity(Aerodrome ae, DatasetEntity dse){
        this(
            null, dse,
            ae.code(), ae.name(),
            ae.servedCity(), ae.siteDescription(), ae.adminAuthority(),
            ae.coordinates(),
            ae.elevation().to(FOOT).getValue().doubleValue(),
            Optional.ofNullable(ae.geoidUndulation()).map(q -> q.to(FOOT).getValue().doubleValue()).orElse(null),
            ae.magVar(), Optional.ofNullable(ae.magVarUpdated()).map(Year::getValue).orElse(null),
            ae.runways().stream().map(Runway::toEntity).collect(Collectors.toList()),
            ae.contactInfos(),
            Optional.ofNullable(ae.ctr()).map(as -> as.toEntity(dse)).orElse(null)
        );
    }

    @Id
    @Getter(AccessLevel.PACKAGE)
    BigInteger id;

    @DBRef
    @NonNull
    @Field("ds")
    DatasetEntity dataset;

    @NonNull
    @Field("code")
    String code;

    @NonNull
    @Field("name")
    String name;

    @Nullable
    @Field("city")
    String servedCity;

    @Nullable
    @Field("site")
    String siteDescription;

    @Nullable
    @Field("admin")
    String adminAuthority;

    @NonNull
    @Field("loc")
    @GeoSpatialIndexed(name = "ad_loc_idx", type = GeoSpatialIndexType.GEO_2DSPHERE)
    Point coordinates;

    @Field("elevFt")
    @Getter(AccessLevel.NONE)
    double elevationFt;

    public Quantity<Length> elevation(){
        return getQuantity(elevationFt, FOOT);
    }

    @Nullable
    @Field("geoidUndFt")
    @Getter(AccessLevel.NONE)
    Double geoidUndulationFt;

    public Optional<Quantity<Length>> geoidUndulation(){
        return Optional.ofNullable(geoidUndulationFt).map(v -> getQuantity(v, FOOT));
    }

    @Nullable
    @Field("magVar")
    Double magVar;

    @Nullable
    @Field("magVarYear")
    Integer magVarYear;

    @Field("rwys")
    List<RunwayEntity> runways;

    @Field("contact")
    Map<String, String> contactInfos;

    @Nullable
    @DBRef
    @With
    @Indexed(name = "ad_ctr_idx")
    AirspaceEntity ctr;

}
