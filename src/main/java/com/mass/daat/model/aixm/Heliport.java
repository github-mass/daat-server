package com.mass.daat.model.aixm;

import com.mass.daat.db.DatasetEntity;
import com.mass.daat.db.HeliportEntity;
import lombok.*;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.time.Year;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class Heliport {

    /**
     * Careful, this is the AIXM internal ID. Use code as an external identifier instead.
     */
    @NonNull String id;

    @NonNull String code;

    @NonNull String name;

    @NonNull Point coordinates;

    @NonNull Quantity<Length> elevation;

    @Nullable
    Quantity<Length> geoidUndulation;

    @Nullable
    Double magVar;

    @Nullable
    Year magVarUpdated;

    @Singular
    List<TakeOffLandingArea> takeoffLandingAreas;

    @Nullable
    @With
    String adminAuthority;

    @Singular
    Map<String, String> contactInfos;

    public HeliportEntity toEntity(@org.springframework.lang.NonNull DatasetEntity dse){
        return new HeliportEntity(this, dse);
    }
}
