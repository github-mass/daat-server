package com.mass.flightplan.util;

import org.locationtech.jts.geom.Geometry;
import org.springframework.lang.NonNull;

public interface GeometryFixer {

    @NonNull
    Geometry fix(@NonNull Geometry geom);

}
