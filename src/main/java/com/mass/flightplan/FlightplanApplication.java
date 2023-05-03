package com.mass.flightplan;

import com.mass.flightplan.geo.AltitudeServiceProperties;
import com.mass.flightplan.vac.VACAtlasProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = {DataSourceAutoConfiguration.class}
)
@EnableConfigurationProperties(
        {
            VACAtlasProperties.class,
            AltitudeServiceProperties.class,
        }
)
@EnableScheduling
public class FlightplanApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightplanApplication.class, args);
    }


}
