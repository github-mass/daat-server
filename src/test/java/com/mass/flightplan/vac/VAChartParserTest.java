package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = MongoAutoConfiguration.class)
@Log4j2
@ActiveProfiles("test")
class VAChartParserTest {

    @Autowired
    private VAChartParser parser;

    @Test
    void parseHeliportInfo()
        throws IOException, InterruptedException
    {
        Resource res = new FileSystemResource("./vac-atlas/test/HMTL.pdf");
        HeliportInfo info = parser.parseHeliportInfo(res, "CODE", "NAME");

        assertThat(info).isNotNull();
        //TODO etc...

        log.info("Parsed heliport info: {}", info);
    }

    @Test
    void parseAirportInfo()
        throws IOException, InterruptedException
    {
        Resource res = new FileSystemResource("./vac-atlas/test/LFPG.pdf");
        AirportInfo info = parser.parseAirportInfo(res, "CODE", "NAME");

        assertThat(info).isNotNull();

        log.info("Parsed airport info: {}", info);
    }
}