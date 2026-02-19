package com.mass.daat.geo;

import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = GeoConfiguration.class)
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, EmbeddedMongoAutoConfiguration.class, MongoAutoConfiguration.class})
@ActiveProfiles("aixm-test")
@Slf4j
@TestPropertySource(properties = {
    "logging.level.com.mass.daat.geo=TRACE"
})
class IgnAltitudeServiceTest {

    @Autowired AltitudeService altitudeService;

    @MockitoBean
    ProximityService proximityService;

    @Test
    public void basicServiceTest(){
        Point coord = new Point(
            2 + 33d / 60 + 1d / 3600,
            51 + 2d / 60 + 26d / 3600
        );

        var resp = altitudeService.getAltitudeAt(coord);

        log.info("{}", resp);

        assertThat(resp.getValue().doubleValue()).isLessThan(0);
    }

}