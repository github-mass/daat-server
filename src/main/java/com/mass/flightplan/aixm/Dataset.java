package com.mass.flightplan.aixm;

import com.mass.flightplan.db.DatasetEntity;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class Dataset {

    String sourceType;
    String sourceName;
    String origin;
    Instant created;
    Instant effective;

    public DatasetEntity toEntity() {
        return new DatasetEntity(
            null,
            sourceType(), sourceName(),
            null,
            origin(),
            created(), effective()
        );
    }
}
