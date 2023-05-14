package com.mass.flightplan.db;

import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface AerodromeRepository
    extends CrudRepository<AerodromeEntity, BigInteger>
{
    Optional<AerodromeEntity> findByDatasetAndCode(@NonNull DatasetEntity dse, @NonNull String code);

    List<AerodromeEntity> findByDatasetAndCoordinatesNear(@NonNull DatasetEntity dse, @NonNull GeoJsonPoint location, @NonNull Distance distance);

    void deleteAllByDataset(@NonNull DatasetEntity dse);
}
