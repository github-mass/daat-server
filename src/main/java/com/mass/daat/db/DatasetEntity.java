package com.mass.daat.db;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.lang.Nullable;

import java.math.BigInteger;
import java.time.Instant;

@Document(collection = "datasets")
@Data
@Builder
@AllArgsConstructor(onConstructor_={@PersistenceCreator})
public class DatasetEntity {

    @Id
    @Setter(AccessLevel.NONE)
    BigInteger id;

    @Field("type")
    String datasetType;

    @Field("name")
    String sourceName;

    @Field("srcDesc")
    String sourceDescription;

    @Field("deprecated")
    @Nullable
    Boolean deprecated;

    @Field("imported")
    @Indexed(direction = IndexDirection.DESCENDING)
    Instant imported;

    @Field("origin")
    String origin;

    @Field("created")
    Instant created;

    @Field("effective")
    @Indexed(direction = IndexDirection.DESCENDING)
    Instant effective;

}
