package com.mass.flightplan.db;

import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.CrudRepository;

public interface VacCrudRepository
    extends CrudRepository<AirportEntity, String>
{
}
