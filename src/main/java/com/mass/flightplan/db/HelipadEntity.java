package com.mass.flightplan.db;

import com.mass.flightplan.vac.HelipadInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.geotools.measure.Units;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import si.uom.quantity.impl.LengthAmount;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.time.Instant;

import static java.time.Instant.now;

@Document(collection = "hvacs")
@Data
@Builder
@AllArgsConstructor(onConstructor_={@PersistenceCreator})
public class HelipadEntity {

    public HelipadEntity(HelipadInfo info){
        this(
            info.code(), info.name(), now(), info.eAipVersion(), false, info.altitude(), new Point(info.longitude(), info.latitude()) , info.localPressure(),
            info.magneticDeclination(), info.contactInfo()
        );
    }

    @NonNull @Id String code;

    @NonNull String name;

    @Builder.Default
    @NonNull Instant updated = now();

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

    @Field(name = "mag_dec")
    double magneticDeclination;

    @Field(name = "contact")
    @NonNull String contactInfo;

    public final Quantity<Length> altitude(){
        return new LengthAmount(altitudeInMetres, Units.METRE);
    }
}
