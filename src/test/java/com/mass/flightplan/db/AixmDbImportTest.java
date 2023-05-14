package com.mass.flightplan.db;

import com.mass.flightplan.ApplicationConfiguration;
import com.mass.flightplan.aixm.AixmImporter;
import com.mass.flightplan.geo.AltitudeService;
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
import si.uom.quantity.impl.LengthAmount;

@DataMongoTest
@Slf4j
@ActiveProfiles("test")
@Import({ApplicationConfiguration.class})
public class AixmDbImportTest {

    static AixmImporter.Result result;

    @BeforeAll
    static void prepareData()
        throws Exception
    {
        log.info("Preparing data...");

        AltitudeService altitudeService = coordinate -> new LengthAmount(69, SI.METRE);
        AixmImporter imp = AixmImporter.builder()
                                       .source(new FileSystemResource("./aixm/export_xml_bd_sia_2023-04-20-p2"))
                                       .parseSiaExport(true)
                                       .sourceType("SIA")
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
