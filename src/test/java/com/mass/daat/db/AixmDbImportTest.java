package com.mass.daat.db;

import com.mass.daat.ApplicationConfiguration;
import com.mass.daat.geo.AltitudeService;
import com.mass.daat.model.aixm.AixmImporter;
import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import java.util.Random;

@DataMongoTest()
@ExtendWith(SpringExtension.class)
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

    @MockitoBean
    AltitudeService altitudeService;

    @Autowired
    AixmDbImporter dbImporter;

    @Test
    public void importDataIntoDb(){
        dbImporter.importResult(result);
    }

}
