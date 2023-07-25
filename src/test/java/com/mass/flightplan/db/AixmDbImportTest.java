package com.mass.flightplan.db;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mass.flightplan.ApplicationConfiguration;
import com.mass.flightplan.geo.AltitudeService;
import com.mass.flightplan.model.aixm.AixmImporter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.test.context.ActiveProfiles;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import java.util.Map;
import java.util.Random;

@DataMongoTest
@Slf4j
@ActiveProfiles("aixm-test")
@Import({ApplicationConfiguration.class})
public class AixmDbImportTest {

    static AixmImporter.Result result;

    @TestConfiguration
    static class TestConfig {
        @Bean
        GeometryConverter geometryConverter(MongoConverter mongoConv) {
            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JtsModule(6));
            ConversionService conversionService = mongoConv.getConversionService();

            return geometry -> {
                Map<String, Object> json = om.convertValue(geometry, new TypeReference<Map<String, Object>>() {
                });
                Document doc = new Document(json);
                GeoJson<?> ret = conversionService.convert(doc, GeoJson.class);

                if (ret == null) {
                    throw new IllegalArgumentException("Geometry evaluated to null: " + geometry);
                }

                return ret;
            };
        }
    }

    @BeforeAll
    static void prepareData()
        throws Exception
    {
        log.info("Preparing data...");

        final Random random = new Random();
        AltitudeService altitudeService = coordinate -> Quantities.getQuantity(random.nextInt(), SI.METRE);
        AixmImporter imp = AixmImporter.builder()
                                       .source(new FileSystemResource("./data/aixm/export_xml_bd_sia_2023-04-20-p2"))
                                       .parseSiaExport(true)
                                       .altitudeService(altitudeService)
                                       .sourceName("test-data")
                                       .build();

        result = imp.perform();
    }

    @MockBean
    AltitudeService altitudeService;

    @Autowired
    AixmDbImporter dbImporter;

    @Test
    public void importDataIntoDb(){
        dbImporter.importResult(result);
    }

}
