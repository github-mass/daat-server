package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.util.List;

@Value
@Builder
@ToString
public class AirportInfo {
    @NonNull String code, name, eAipVersion;

    /**
     * Altitude in meters AMSL
     */
    double altitude;

    double latitude;

    double longitude;

    int localPressure;

    double magneticDeclination;

    @NonNull String contactInfo;

    @NonNull List<RunwayInfo> runways;
}
