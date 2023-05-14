package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = VacUtilitiesConfiguration.class)
@Log4j2
@ActiveProfiles("test")
@Import(VacUtilitiesConfiguration.class)
class VACAtlasParserTest {

    @Autowired
    VACAtlasParser atlasParser;

    @Test
    public void testVacAirportList()
        throws IOException, ExecutionException
    {
        Map<String, String> airports = atlasParser.fetchAirportMap();

        assertThat(airports).isNotEmpty();

        log.info("{} airports: \n{}", airports.size(), airports);
    }

    @Test
    public void testVacHeliportList()
        throws IOException, ExecutionException
    {
        Map<String, String> heliports = atlasParser.fetchHelipadMap();

        assertThat(heliports).isNotEmpty();

        log.info("{} heliports: \n{}", heliports.size(), heliports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LFIP"})
    public void testAirportCardDownload(String code)
            throws IOException
    {
        Resource res = atlasParser.fetchAirportVacCard(code);

        assertThat(res).isNotNull();

        long len = res.contentLength();
        assertThat(len).withFailMessage("Expected VAC resource to be at least 50kB, but got: %dkB", len >>> 10).isGreaterThanOrEqualTo(50 << 10);

        Path tmp = Paths.get("./test-atlas/vac/" + code + ".pdf");
        Files.createDirectories(tmp.getParent());
        try (OutputStream out = Files.newOutputStream(tmp, CREATE, TRUNCATE_EXISTING)) {
            StreamUtils.copy(res.getInputStream(), out);
        }

        log.info("Written {} bytes to {}", len, tmp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LFWN"})
    public void testHeliportCardDownload(String code)
            throws IOException
    {
        Resource res = atlasParser.fetchHelipadVacCard(code);

        assertThat(res).isNotNull();

        long len = res.contentLength();
        assertThat(len).withFailMessage("Expected VAC resource to be at least 50kB, but got: %dkB", len >>> 10).isGreaterThanOrEqualTo(50 << 10);

        Path tmp = Paths.get("./test-atlas/hvac/" + code + ".pdf");
        Files.createDirectories(tmp.getParent());
        try (OutputStream out = Files.newOutputStream(tmp, CREATE, TRUNCATE_EXISTING)) {
            StreamUtils.copy(res.getInputStream(), out);
        }

        log.info("Wrote {} bytes to {}", len, tmp);
    }
}