package com.mass.flightplan.db;

import com.mongodb.MongoClientSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.MongoPropertiesClientSettingsBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.lang.NonNull;

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
}
