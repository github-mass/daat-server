package com.mass.daat.db;

import com.mass.daat.model.aixm.AixmImporter;
import com.mass.daat.model.zicad.ZicadImporter;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.math.BigInteger;
import java.util.List;

public interface DatasetRepository
    extends CrudRepository<DatasetEntity, BigInteger>
{

    List<DatasetEntity> findAllByDatasetType(@NonNull String type);

    @Query(value = "{'effective': {$lt: ISODate()}, 'imported': {$lt: ISODate()}, 'type': ?0}", sort = "{'effective': -1, 'imported': -1}")
    List<DatasetEntity> findFirstCurrentByDatasetType(@NonNull String type);

    @Nullable
    default DatasetEntity current(String type){
        return findFirstCurrentByDatasetType(type).stream().findFirst().orElse(null);
    }

    @Nullable
    default DatasetEntity currentAixm(){
        return current(AixmImporter.DATASET_TYPE);
    }

    @Nullable
    default DatasetEntity currentZicad(){
        return current(ZicadImporter.DATASET_TYPE);
    }

    default List<DatasetEntity> findAllAixm(){
        return findAllByDatasetType(AixmImporter.DATASET_TYPE);
    }

    default List<DatasetEntity> findAllZicad(){
        return findAllByDatasetType(ZicadImporter.DATASET_TYPE);
    }

}

