package com.mass.daat.util;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

@RequiredArgsConstructor
public class DouglasPeuckerGeometryFixer
    implements GeometryFixer
{
    @Override
    public @NotNull Geometry fix(@NotNull Geometry geom) {
        return DouglasPeuckerSimplifier.simplify(geom, 0.000001);
    }
}
