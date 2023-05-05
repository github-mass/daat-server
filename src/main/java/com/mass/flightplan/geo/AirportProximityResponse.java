package com.mass.flightplan.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Value
@Builder
@ToString
@Getter(onMethod = @__(@JsonProperty))
public class AirportProximityResponse {

    @NonNull Point location;

    double altitude;

    @Singular
    List<ProximateHelipad> proximateHelipads;

    @Singular
    List<ProximateAirports> proximateAirports;

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    public static class ProximateHelipad {
        @NonNull String code;
        @NonNull String name;
        @NonNull Instant updated;
        double altitude;
        Point coordinates;
        int qfe;
        @NonNull String contact;
        @Nullable String vacUrl;
        double dc;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    public static class ProximateAirports {
        @NonNull String code;
        @NonNull String name;
        @NonNull Instant updated;
        double altitude;
        Point coordinates;
        double distance;
        int qfe;
        @NonNull String contact;
        @Nullable String vacUrl;
        boolean runwaysOk;
        @Singular
        Set<ProximateRunway> runways;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    public static class ProximateRunway {
        String runway;
        double qfu;
        double length;
        boolean paved;
        double distToAxis;
        double distOnAxis;
    }

}
