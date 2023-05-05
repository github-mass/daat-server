package com.mass.flightplan.db;

import com.mass.flightplan.vac.AirportInfo;
import com.mass.flightplan.vac.RunwayInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.geotools.measure.Units;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import si.uom.quantity.impl.LengthAmount;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.time.Instant;
import java.util.List;

import static java.time.Instant.now;

@Document(collection = "vacs")
@TypeAlias("airport")
@Data
@Builder
@AllArgsConstructor(onConstructor_={@PersistenceCreator})
public class AirportEntity {

    public AirportEntity(@NonNull AirportInfo info){
        this(
            info.code(), info.name(), now(), info.eAipVersion(), false, info.altitude(), new Point(info.longitude(), info.latitude()), info.localPressure(),
            info.magneticDeclination(), info.contactInfo(), !info.runways().isEmpty(), info.runways()
        );
    }

    @NonNull @Id String code;

    @NonNull String name;

    @Builder.Default
    @NonNull
    Instant updated = now();

    @NonNull
    @Field(name = "version")
    String eAipVersion;

    @Builder.Default
    boolean forceUpdate = false;

    /**
     * Altitude in meters AMSL
     */
    @Field(name = "alt")
    double altitudeInMetres;

    @Field(name = "loc")
    @GeoSpatialIndexed(name = "vac_loc_idx", type = GeoSpatialIndexType.GEO_2DSPHERE)
    Point coordinates;

    @Field(name = "qfe")
    int localPressure;

    /**
     * Magnetic declination. If positive towards E, if negative towards W.
     */
    @Field(name = "mag_dec")
    double magneticDeclination;

    @Field(name = "contact")
    @NonNull String contactInfo;

    boolean runwaysOk;

    @NonNull List<RunwayInfo> runways;

    public final Quantity<Length> altitude(){
        return new LengthAmount(altitudeInMetres, Units.METRE);
    }

}
