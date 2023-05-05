package com.mass.flightplan.db;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface HvacCrudRepository
    extends CrudRepository<HelipadEntity, String>
{
    List<HelipadEntity> findByCoordinatesNear(Point point, Distance max);
}
