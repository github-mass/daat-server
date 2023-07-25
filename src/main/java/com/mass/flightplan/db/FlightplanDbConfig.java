package com.mass.flightplan.db;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.MongoPropertiesClientSettingsBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.lang.NonNull;

import java.util.Map;

@Configuration
@EnableMongoRepositories
@EnableConfigurationProperties(MongoProperties.class)
@RequiredArgsConstructor
public class FlightplanDbConfig
    extends AbstractMongoClientConfiguration
{
    private final MongoProperties mongoProperties;

    @Override
    @NonNull
    protected String getDatabaseName() {
        return "flightplan";
    }

    @Override
    protected void configureClientSettings(MongoClientSettings.Builder builder) {
        new MongoPropertiesClientSettingsBuilderCustomizer(mongoProperties).customize(builder);
    }

    @Override
    @Bean
    @NonNull
    public MappingMongoConverter mappingMongoConverter(@NonNull MongoDatabaseFactory databaseFactory, @NonNull MongoCustomConversions customConversions, @NonNull MongoMappingContext mappingContext)
    {
        var ret = super.mappingMongoConverter(databaseFactory, customConversions, mappingContext);

        /*
         *  Suppress "_class" field in serialized objects. See DefaultMongoTypeMapper
         */
        ret.setTypeMapper(new DefaultMongoTypeMapper(null));

        return ret;
    }

    @Bean
    public MongoTransactionManager mongoTxManager(){
        return new MongoTransactionManager(mongoDbFactory());
    }

    @Override
    protected boolean autoIndexCreation() {
        return true;
    }

    @Bean
    @Lazy(false)
    public GeometryConverter geometryConverter(MongoConverter mc){
        ConversionService conversionService = mc.getConversionService();

        if(!conversionService.canConvert(Document.class, GeoJson.class)){
            throw new IllegalStateException("No converter found %s -> %s".formatted(Document.class, GeoJson.class));
        }

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JtsModule(6)); // num of precision digits, veery important!

        return geometry -> {
            Map<String, Object> json = om.convertValue(geometry, new TypeReference<Map<String, Object>>() {});
            Document doc = new Document(json);
            GeoJson<?> ret = conversionService.convert(doc, GeoJson.class);

            if(ret == null){
                throw new IllegalArgumentException("Geometry evaluated to null: " + geometry);
            }

            return ret;
        };
    }
}
