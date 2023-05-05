package com.mass.flightplan.geo;

import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class, EmbeddedMongoAutoConfiguration.class})
@ActiveProfiles("test")
@Log4j2
@TestPropertySource(properties = {
    "logging.level.com.mass.flightplan.geo=TRACE"
})
class IgnAltitudeServiceTest {

    @Autowired AltitudeService altitudeService;

    @Test
    public void basicServiceTest(){
        Point coord = new Point(
            2 + 33d / 60 + 1d / 3600,
            51 + 2d / 60 + 26d / 3600
        );

        var resp = altitudeService.getAltitudeAt(coord);

        log.info(resp);

        assertThat(resp.getValue().doubleValue()).isLessThan(0);
    }

}