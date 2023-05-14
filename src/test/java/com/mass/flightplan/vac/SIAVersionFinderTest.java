package com.mass.flightplan.vac;

import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {VacUtilitiesConfiguration.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, EmbeddedMongoAutoConfiguration.class, MongoAutoConfiguration.class})
@TestPropertySource(properties = {
    "vac-atlas.e-aip-version=shalalala",
    "sia.auto-version.enabled=false",
    "spring.cloud.refresh.enabled=true",
})
@Slf4j
@ActiveProfiles("test")
class SIAVersionFinderTest {

    @Autowired
    VACAtlasProperties atlasProperties;

    @Autowired
    SIAVersionFinder finder;

    @Test
    public void testSIAVersionUpdate(){
        log.info("Before: {}", atlasProperties);
        assertThat(atlasProperties.getEAipVersion()).isEqualTo("shalalala");

        finder.updateSiaVersion();

        log.info("SIA version updated to: " + atlasProperties.getEAipVersion());
        assertThat(atlasProperties.getEAipVersion()).isNotEqualTo("shalalala");

        log.info("After: {}", atlasProperties);
    }

}