package com.mass.flightplan.geo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProximityResponse {

    @NonNull
    @JsonProperty("query")
    Point location;

    @JsonProperty(value = "alt_m")
    Double altitudeM;

    @JsonProperty("datasets")
    @Singular
    List<DatasetInfo> datasets;

    @JsonProperty("proxHp")
    @Singular
    List<ProximateHeliport> proximateHeliports;

    @JsonProperty("proxAd")
    @Singular
    List<ProximateAerodrome> proximateAerodromes;

    @JsonProperty("proxAs")
    @Singular
    List<ProximateAirspace> proximateAirspaces;

    @JsonProperty("proxZicad")
    @Singular
    List<ProximateZicad> proximateZicads;


    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    public static class DatasetInfo {
        @JsonProperty("source")
        String source;

//        @JsonProperty("created")
//        Instant created;

        @JsonProperty("effective")
        Instant effective;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    public static class ProximateHeliport {

        @JsonProperty("code")
        @NonNull String code;

        @JsonProperty("name")
        @NonNull String name;

        @JsonProperty("alt_m")
        double altitudeM;

        @JsonProperty("loc")
        Point coordinates;

        @JsonProperty("dist_m")
        double distanceM;

        /**
         * True bearing from query to aerodrome.
         */
        @JsonProperty("quj")
        double quj;

        @JsonProperty("admin")
        @Nullable String admin;

        @JsonProperty("contact")
        @Nullable
        Map<String, String> contact;

        @JsonProperty("minDc_m")
        double minDcInMetres;

        @Singular
        @JsonProperty("tlas")
        List<ProximateHeliportTla> takeOffLandingAreas;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ProximateHeliportTla {
        @JsonProperty("desig")
        String designation;

        @JsonProperty("alt_m")
        Double altitudeM;

        @JsonProperty("comp")
        String composition;

        @JsonProperty("remark")
        String remark;

        @JsonProperty("dc_m")
        double dcM;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    public static class ProximateAerodrome {

        @JsonProperty("code")
        @NonNull String code;

        @JsonProperty("name")
        @NonNull String name;

        @JsonProperty("city")
        String servedCity;

        @JsonProperty("site")
        String siteDescription;

        @JsonProperty("alt_m")
        double altitudeM;

        @JsonProperty("loc")
        Point coordinates;

        @JsonProperty("dist_m")
        double distanceM;

        /**
         * True bearing from query to aerodrome.
         */
        @JsonProperty("quj")
        double quj;

        @JsonProperty("admin")
        String adminAuthority;

        @JsonProperty("contact")
        @NonNull Map<String, String> contact;

        @JsonProperty("rwys")
        @Singular
        Set<ProximateRunway> runways;

        @JsonProperty("hasCtr")
        boolean hasCtr;

        @JsonProperty("inCtr")
        boolean inCtr;

        @JsonProperty("minDa_m")
        double minDistToAxisM;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ProximateRunway {
        @JsonProperty("rwy")
        String runway;

        @JsonProperty("loc")
        Point coordinates;

        @JsonProperty("trueBrg")
        double trueBearing;

        @JsonProperty("minAlt_m")
        double minAltitudeM;

        @JsonProperty("maxAlt_m")
        double maxAltitudeM;

        @JsonProperty("length_m")
        double lengthM;

        @JsonProperty("width_m")
        double widthM;

        @JsonProperty("comp")
        String composition;

        @JsonProperty("paved")
        boolean paved;

        @JsonProperty("distToAxis_m")
        double distToAxisM;

        @JsonProperty("distOnAxis_m")
        double distOnAxisM;

        /**
         * Azimuth of the query location wrt to the runway orientation.
         */
        @JsonProperty("azimuthToQuery")
        double azimuthToQuery;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ProximateAirspace {
        @JsonProperty("type")
        String type;

        @JsonProperty("code")
        String code;

        @JsonProperty("name")
        String name;

        @JsonProperty("remark")
        @Nullable
        String remark;

        @JsonProperty("activationType")
        @Nullable
        String activationType;

        @JsonProperty("activationRemark")
        @Nullable
        String activationRemark;

        @JsonProperty("minFloor")
        String minFloor;

        @JsonProperty("maxCeiling")
        String maxCeiling;

        @JsonProperty("frontiers")
        @Nullable
        Set<String> frontiers;

        @JsonProperty("dist_m")
        double distanceM;

        @JsonProperty("quj")
        double quj;
    }

    @Value
    @Builder
    @EqualsAndHashCode
    @Getter(onMethod = @__(@JsonProperty))
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ProximateZicad {
        @JsonProperty("areaId")
        String areaId;

        @JsonProperty("name")
        String name;

        @JsonProperty("commune")
        String commune;

        @JsonProperty("authority")
        String authority;

        @JsonProperty("effective")
        LocalDate effective;

        @JsonProperty("dist_m")
        double distanceM;

        @JsonProperty("quj")
        double quj;
    }
}
