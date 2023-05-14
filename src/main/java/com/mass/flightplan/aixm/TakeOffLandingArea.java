package com.mass.flightplan.aixm;

import com.mass.flightplan.db.TlaEntity;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.springframework.data.geo.Point;
import org.springframework.lang.Nullable;

import javax.measure.quantity.Length;

@Value
@Builder
public class TakeOffLandingArea {
    @NonNull String designation;
    @NonNull Point coordinates;
    @Nullable Length elevation;
    @Nullable Length width;
    @Nullable Length length;
    @Nullable String composition;
    @Nullable String remark;

    public TlaEntity toEntity(){
        return new TlaEntity(this);
    }
}
