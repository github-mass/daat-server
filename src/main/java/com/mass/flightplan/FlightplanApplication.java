package com.mass.flightplan;

import com.mass.flightplan.vac.VACAtlasProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(
    exclude = {DataSourceAutoConfiguration.class}
)
@EnableConfigurationProperties(
        VACAtlasProperties.class
)
public class FlightplanApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightplanApplication.class, args);
    }


}
