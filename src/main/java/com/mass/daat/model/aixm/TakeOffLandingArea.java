package com.mass.daat.model.aixm;

import com.mass.daat.db.TlaEntity;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import javax.measure.Quantity;
import javax.measure.quantity.Length;

@Value
@Builder
public class TakeOffLandingArea {
    @NonNull String designation;
    @NonNull Point coordinates;
    @Nullable Quantity<Length> elevation;
    @Nullable Quantity<Length> width;
    @Nullable Quantity<Length> length;
    @Nullable String composition;
    @Nullable String remark;

    public TlaEntity toEntity(){
        return new TlaEntity(this);
    }
}
