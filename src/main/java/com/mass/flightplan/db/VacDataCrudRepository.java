package com.mass.flightplan.db;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface VacDataCrudRepository
    extends CrudRepository<VacDataEntity, String>
{
    Optional<UrlView> findByCodeEqualsAndTypeEquals(String code, VacDataEntity.Type type);


    interface UrlView {
        String getUrl();
    }
}
