package com.mass.flightplan.db;

import com.mongodb.lang.NonNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
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

    @NonNull
    private String url;

    @NonNull
    private String code;

    @NonNull
    private Type type;

    @NonNull
    private byte[] data;

    public enum Type {
        VAC,
        HVAC
    }
}
