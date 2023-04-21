package com.mass.flightplan.vac;


import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.springframework.data.util.Pair;

@Value
@Builder
@ToString
public class RunwayInfo {
    Pair<String, String> shortOrientation;
    Pair<Integer, Integer> magneticOrientation;
    int length;
    int width;
    boolean paved;
}
