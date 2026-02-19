package com.mass.daat;

import com.mass.daat.db.AixmDbImporter;
import com.mass.daat.db.DatasetRepository;
import com.mass.daat.db.SpringGeometryConverter;
import com.mass.daat.db.ZicadDbImporter;
import com.mass.daat.geo.AltitudeService;
import com.mass.daat.util.DouglasPeuckerGeometryFixer;
import com.mass.daat.util.GeometryConverter;
import com.mass.daat.util.GeometryFixer;
import org.bson.Document;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.lang.NonNull;

@Configuration
public class ApplicationConfiguration {

    @Bean
    @Lazy(false)
    public GeometryConverter geometryConverter(MongoConverter mc){
        ConversionService conversionService = mc.getConversionService();

        if(!conversionService.canConvert(Document.class, GeoJson.class)){
            throw new IllegalStateException("No converter found %s -> %s".formatted(Document.class, GeoJson.class));
        }

        return new SpringGeometryConverter(mc.getConversionService());
    }

    @Bean
    public GeometryFixer geometryFixer(){
        return new DouglasPeuckerGeometryFixer();
    }

    @Bean
    @RefreshScope
    public AixmProperties aixmProperties(){
        return new AixmProperties();
    }

    @Bean
    public AixmDbImporter aixmDbImporter(@NonNull MongoTemplate mongo, @NonNull GeometryConverter geometryConverter, @NonNull GeometryFixer geometryFixer){
        return new AixmDbImporter(mongo, geometryConverter, geometryFixer);
    }

    @Bean
    public AixmUpdateService aixmUpdateService(
        @NonNull AixmProperties properties,
        @NonNull AltitudeService altitudeService,
        @NonNull AixmDbImporter dbImporter,
        @NonNull DatasetRepository dataSetRepo
    ){
        return new AixmUpdateService(properties, altitudeService, dbImporter, dataSetRepo);
    }

    @Bean
    @RefreshScope
    public ZicadProperties zicadProperties(){
        return new ZicadProperties();
    }

    @Bean
    public ZicadDbImporter zicadDbImporter(@NonNull MongoTemplate mongo, @NonNull GeometryConverter geometryConverter){
        return new ZicadDbImporter(mongo, geometryConverter);
    }

    @Bean
    public ZicadUpdateService zicadUpdateService(
        @NonNull ZicadProperties properties,
        @NonNull ZicadDbImporter dbImporter,
        @NonNull DatasetRepository dataSetRepo
    ){
        return new ZicadUpdateService(properties, dbImporter, dataSetRepo);
    }

}
