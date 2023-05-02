package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = MongoAutoConfiguration.class)
@Log4j2
@ActiveProfiles("test")
class VAChartParserTest {

    @Autowired
    private VAChartParser parser;

    @Autowired
    private VACAtlasProperties atlasProperties;

    @ParameterizedTest
    @MethodSource("listHvacCardsInLocalAtlas")
    public void parseHeliportInfo(Path file, String code)
        throws IOException, InterruptedException
    {
        assumeThat(atlasProperties.getIgnoredAirports())
            .describedAs("Ignoring airports in to-ignore list")
            .doesNotContain(code);

        Resource res = new FileSystemResource(file);
        HelipadInfo info = parser.parseHeliportInfo(res, code, "NAME");

        assertThat(info).isNotNull();
        //TODO etc...

        log.info("Parsed heliport info: {}", info);
    }

    static Collection<Arguments> listHvacCardsInLocalAtlas()
        throws IOException
    {
        return Files.list(Path.of("./test-atlas/hvac/"))
                    .filter(p -> p.getFileName().toString().endsWith(".pdf"))
                    .map(p -> {
                        String file = p.getFileName().toString();
                        String code = file.substring(0, file.indexOf('.'));
                        return Arguments.of(p, code);
                    }).toList();
    }

    @ParameterizedTest
    @MethodSource("listVacCardsInLocalAtlas")
    public void parseAirportInfo(Path file, String code)
        throws IOException, InterruptedException
    {
        assumeThat(atlasProperties.getIgnoredAirports())
            .describedAs("Ignoring airports in to-ignore list")
            .doesNotContain(code);

        Resource res = new FileSystemResource(file);
        AirportInfo info = parser.parseAirportInfo(res, code, "NAME");

        assertThat(info).isNotNull();

        assertThat(info).as("Verify runways extraction").satisfiesAnyOf(
            i -> assertThat(i.runways()).isNotEmpty(),
            i -> assertThat(atlasProperties.getIgnoreRunwayExtractionErrors()).contains(i.code())
        );

        log.info("Parsed airport info: {}", info);
    }

    static Collection<Arguments> listVacCardsInLocalAtlas()
        throws IOException
    {
        return Files.list(Path.of("./test-atlas/vac/"))
                    .filter(p -> p.getFileName().toString().endsWith(".pdf"))
                    .map(p -> {
                        String file = p.getFileName().toString();
                        String code = file.substring(0, file.indexOf('.'));
                        return Arguments.of(p, code);
                    }).toList();
    }

    @Test
    @Disabled
        //for manual debug
    void parseAirportInfoSingle()
        throws IOException, InterruptedException
    {
        String code = "LFTZ";
        parseAirportInfo(Path.of("./test-atlas/vac/" + code + ".pdf"), code);
    }

    @Test
    @Disabled
        //for manual debug
    void parseHelipadInfoSingle()
        throws IOException, InterruptedException
    {
        String code = "";
        parseAirportInfo(Path.of("./test-atlas/hvac" + code + ".pdf"), code);
    }
}