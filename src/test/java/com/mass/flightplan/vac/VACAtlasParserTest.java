package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, DataSourceAutoConfiguration.class})
@Log4j2
@ActiveProfiles("test")
class VACAtlasParserTest {

    @Autowired
    VACAtlasParser atlasParser;

    @Test
    public void testVacAirportList() {
        Map<String, String> airports = atlasParser.getAirportMap();

        assertThat(airports).isNotEmpty();

        log.info("{} airports: \n{}", airports.size(), airports);
    }

    @Test
    public void testVacHeliportList() {
        Map<String, String> heliports = atlasParser.getHeliportMap();

        assertThat(heliports).isNotEmpty();

        log.info("{} heliports: \n{}", heliports.size(), heliports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LFPG"})
    public void testAirportCardDownload(String code)
            throws IOException
    {
        Resource res = atlasParser.fetchAirportVacCard(code);

        assertThat(res).isNotNull();

        long len = res.contentLength();
        assertThat(len).withFailMessage("Expected VAC resource to be at least 50kB, but got: %dkB", len >>> 10).isGreaterThanOrEqualTo(50 << 10);

        Path tmp = Paths.get("./vac-atlas/test/" + code + ".pdf");
        Files.createDirectories(tmp.getParent());
        try (OutputStream out = Files.newOutputStream(tmp, CREATE, TRUNCATE_EXISTING)) {
            StreamUtils.copy(res.getInputStream(), out);
        }

        log.info("Written {} bytes to {}", len, tmp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HMTL"})
    public void testHeliportCardDownload(String code)
            throws IOException
    {
        Resource res = atlasParser.fetchHeliportVacCard(code);

        assertThat(res).isNotNull();

        long len = res.contentLength();
        assertThat(len).withFailMessage("Expected VAC resource to be at least 50kB, but got: %dkB", len >>> 10).isGreaterThanOrEqualTo(50 << 10);

        Path tmp = Paths.get("./vac-atlas/test/" + code + ".pdf");
        Files.createDirectories(tmp.getParent());
        try (OutputStream out = Files.newOutputStream(tmp, CREATE, TRUNCATE_EXISTING)) {
            StreamUtils.copy(res.getInputStream(), out);
        }

        log.info("Wrote {} bytes to {}", len, tmp);
    }
}