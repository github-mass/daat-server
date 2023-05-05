package com.mass.flightplan.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.geo.Point;
import org.springframework.test.context.ActiveProfiles;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = EmbeddedMongoAutoConfiguration.class)
@ActiveProfiles("test")
@Import({GeoConfiguration.class, JacksonAutoConfiguration.class})
class AirportProximityServiceTest {

    @Autowired
    AirportProximityService service;

    @Autowired ObjectMapper om;

    @ParameterizedTest
    @MethodSource("proximityLocations")
    public void computeProximityFor(double longitude, double latitude)
        throws JsonProcessingException
    {
        AirportProximityResponse resp = service.computeFor(new Point(longitude, latitude));

        assertThat(resp).isNotNull();

        System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(resp));
    }



    private static Stream<Arguments> proximityLocations(){
        return Stream.of(
            Arguments.of(2.189911, 48.74992)
        );
    }
}