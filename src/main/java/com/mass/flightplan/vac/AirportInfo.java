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
    @NonNull int altitude;
    @NonNull double latitude;
    @NonNull double longitude;
    @NonNull int localPressure;
    @NonNull String contactInfo;
    @NonNull List<RunwayInfo> runways;
}
