package com.mass.flightplan.model.aixm;

import com.mass.flightplan.geo.AltitudeService;
import com.mass.flightplan.model.Dataset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import static org.assertj.core.api.Assertions.assertThat;

class AixmImporterTest {


    final AltitudeService altitudeService = coordinate -> Quantities.getQuantity(69, SI.METRE);

    @Test
    void perform()
        throws Exception
    {
        AixmImporter imp = AixmImporter.builder()
            .source(new FileSystemResource("./data/aixm/export_xml_bd_sia_2023-04-20-p2"))
            .parseSiaExport(true)
            .altitudeService(altitudeService)
            .sourceName("test-data")
            .sourceDescription("TestCase")
            .build();

        var result = imp.perform();

        assertThat(result).isNotNull();

        assertThat(result.dataset()).extracting(Dataset::origin).isNotNull();
        assertThat(result.dataset()).extracting(Dataset::created).isNotNull();
        assertThat(result.dataset()).extracting(Dataset::effective).isNotNull();
        assertThat(result.dataset()).extracting(Dataset::sourceName).isEqualTo("test-data");
        assertThat(result.dataset()).extracting(Dataset::datasetType).isEqualTo("SIA");

        assertThat(result.aerodromes()).isNotEmpty();
        assertThat(result.aerodromes()).hasSize(562);

        assertThat(result.heliports()).isNotEmpty();
        assertThat(result.heliports()).hasSize(288);

        assertThat(result.airspaces()).isNotEmpty();
    }
}