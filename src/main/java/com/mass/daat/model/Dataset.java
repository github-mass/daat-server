package com.mass.daat.model;

import com.mass.daat.db.DatasetEntity;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class Dataset {

    String datasetType;
    String sourceName;
    String sourceDescription;
    String origin;
    Instant created;
    Instant effective;

    public DatasetEntity toEntity() {
        return new DatasetEntity(
            null,
            datasetType(), sourceName(), sourceDescription(),
            false, null,
            origin(),
            created(), effective()
        );
    }
}
