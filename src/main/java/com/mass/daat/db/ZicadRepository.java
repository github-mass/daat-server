package com.mass.daat.db;

import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;

import java.util.List;

public interface ZicadRepository
    extends CrudRepository<ZicadEntity, String>
{
    List<ZicadEntity> findByDatasetAndGeometryNear(@NonNull DatasetEntity dse, @NonNull GeoJsonPoint location, @NonNull Distance distance);
}
