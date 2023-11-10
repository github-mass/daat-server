package com.mass.flightplan.db;

import com.mass.flightplan.ApplicationConfiguration;
import com.mass.flightplan.geo.AltitudeService;
import com.mass.flightplan.model.aixm.AixmImporter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import java.util.Random;

@DataMongoTest
@Slf4j
@ActiveProfiles("aixm-test")
@Import({ApplicationConfiguration.class})
public class AixmDbImportTest {

    static AixmImporter.Result result;

    @BeforeAll
    static void prepareData()
        throws Exception
    {
        log.info("Preparing data...");

        final Random random = new Random();
        AltitudeService altitudeService = coordinate -> Quantities.getQuantity(random.nextInt(), SI.METRE);
        AixmImporter imp = AixmImporter.builder()
                                       .source(new FileSystemResource("data/aixm/export_xml_bd_sia_2023-11-30-x4"))
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
