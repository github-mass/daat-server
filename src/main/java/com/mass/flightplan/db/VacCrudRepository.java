package com.mass.flightplan.db;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface VacCrudRepository
    extends CrudRepository<AirportEntity, String>
{
    List<AirportEntity> findByCoordinatesNear(Point point, Distance max);
}
