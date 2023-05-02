package com.mass.flightplan.db;

import com.mass.flightplan.vac.AirportInfo;
import com.mass.flightplan.vac.RunwayInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
            info.code(), info.name(), now(), info.eAipVersion(), false, info.altitude(), new Point(info.longitude(), info.latitude()), info.localPressure(), info.contactInfo(),
            !info.runways().isEmpty(), info.runways()
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

    @Field(name = "alt")
    int altitude;

    @Field(name = "loc")
    @GeoSpatialIndexed(name = "vac_loc_idx")
    Point coordinates;

    @Field(name = "qfe")
    int localPressure;

    @Field(name = "contact")
    @NonNull String contactInfo;

    @NonNull boolean runwaysOk;

    @NonNull List<RunwayInfo> runways;

}
