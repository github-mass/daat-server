package com.mass.flightplan.vac;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@TestPropertySource(properties = {
    "vac-atlas.e-aip-version=shalalala",
    "sia.auto-version.enabled=false",
    "spring.cloud.refresh.enabled=true"
})
@Log4j2
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