package com.mass.daat.db;

import com.mongodb.MongoClientSettings;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.PropertiesMongoConnectionDetails;
import org.springframework.boot.autoconfigure.mongo.StandardMongoClientSettingsBuilderCustomizer;
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
public class DaatDbConfig
    extends AbstractMongoClientConfiguration
{
    private final MongoProperties mongoProperties;

    @Override
    @NonNull
    protected String getDatabaseName() {
        return "daat";
    }

    @Override
    protected void configureClientSettings(@NotNull MongoClientSettings.Builder builder) {
        new StandardMongoClientSettingsBuilderCustomizer(
                new PropertiesMongoConnectionDetails(mongoProperties, null), //SSL bundles??
                mongoProperties.getUuidRepresentation()
        ).customize(builder);
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
