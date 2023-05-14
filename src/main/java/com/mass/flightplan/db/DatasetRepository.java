package com.mass.flightplan.db;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.math.BigInteger;
import java.util.List;

public interface DatasetRepository
    extends CrudRepository<DatasetEntity, BigInteger>
{

    @Query(value = "{'effective': {$lt: ISODate()}, 'imported': {$lt: ISODate()}}", sort = "{'effective': -1, 'imported': -1}")
    List<DatasetEntity> findFirstCurrent();

    default DatasetEntity current(){
        return findFirstCurrent().stream().findFirst().orElse(null);
    }
}

