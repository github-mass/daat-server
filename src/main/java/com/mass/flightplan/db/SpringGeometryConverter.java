package com.mass.flightplan.db;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mass.flightplan.util.GeometryConverter;
import lombok.NonNull;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Geometry;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.core.geo.GeoJson;

import java.util.Map;

public class SpringGeometryConverter
    implements GeometryConverter
{
    private final @NonNull ConversionService conversionService;

    private final ObjectMapper om;

    public SpringGeometryConverter(@NonNull ConversionService conversionService) {
        this.conversionService = conversionService;

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JtsModule(6)); // num of precision digits, veery important!

        this.om = om;
    }

    @Override
    public @NotNull GeoJson<?> convert(@NotNull Geometry geometry) {
        Map<String, Object> json = om.convertValue(geometry, new TypeReference<Map<String, Object>>() {});
        Document doc = new Document(json);

        GeoJson<?> ret = conversionService.convert(doc, GeoJson.class);

        if (ret == null) {
            throw new IllegalArgumentException("Geometry evaluated to null: " + geometry);
        }

        return ret;
    }
}
