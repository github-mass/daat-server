package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@ToString
public class HelipadInfo {
    @NonNull String code, name, eAipVersion;
    int altitude;
    double latitude;
    double longitude;
    int localPressure;
    double magneticDeclination;
    @NonNull String contactInfo;
}
