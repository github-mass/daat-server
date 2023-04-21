package com.mass.flightplan.vac;

import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
@ToString
public class HeliportInfo {
    @NonNull String code, name;
    @NonNull int altitude;
    @NonNull double latitude;
    @NonNull double longitude;
    @NonNull int localPressure;
    @NonNull String contactInfo;
}
