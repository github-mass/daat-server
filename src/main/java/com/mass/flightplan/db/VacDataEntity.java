package com.mass.flightplan.db;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

import static java.time.Instant.now;

@Document(collection = "vac-data")
@TypeAlias("vachart-pdf")
@Data
@Builder
public class VacDataEntity {

    @Builder.Default
    @Version
    private Instant updated = now();

    private String url;

    private String code;

    private Type type;

    private byte[] data;

    public enum Type {
        VAC,
        HVAC
    }
}
