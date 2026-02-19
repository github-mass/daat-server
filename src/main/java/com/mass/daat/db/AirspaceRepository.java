package com.mass.daat.db;

import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;

import java.math.BigInteger;
import java.util.List;

public interface AirspaceRepository
    extends CrudRepository<AirspaceEntity, BigInteger>
{
    List<AirspaceEntity> findByDatasetAndGeometryNear(@NonNull DatasetEntity dse, @NonNull GeoJsonPoint point, @NonNull Distance distance);

}
