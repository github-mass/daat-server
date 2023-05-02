package com.mass.flightplan.vac;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.springframework.data.annotation.PersistenceCreator;

@Value
@Builder
@ToString
@AllArgsConstructor(onConstructor=@__({@PersistenceCreator}))
public class RunwayInfo {
    String code;
    int qfu;
    double length;
    double width;
    boolean paved;
}
