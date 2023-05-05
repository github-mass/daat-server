package com.mass.flightplan.geo;

import com.mass.flightplan.db.HvacCrudRepository;
import com.mass.flightplan.db.VacCrudRepository;
import com.mass.flightplan.db.VacDataCrudRepository;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.net.ssl.SSLException;

@Configuration
@RequiredArgsConstructor
@Import(WebClientAutoConfiguration.class)
public class GeoConfiguration {

    @ConditionalOnProperty(name = "altitude-service.type", havingValue = "ign")
    @Bean
    public IgnAltitudeService ignAltitudeService(
        AltitudeServiceProperties properties, WebClient.Builder clientBuilder
    )
        throws SSLException
    {
        clientBuilder.clientConnector(ignWebServicesHttpClient());
        return new IgnAltitudeService(clientBuilder.build(), properties.getIgn());
    }

    ClientHttpConnector ignWebServicesHttpClient()
        throws SSLException
    {
        var sslContext = SslContextBuilder.forClient()
                                          .trustManager(GeoConfiguration.class.getResourceAsStream("/wxs-ign-fr.pem"))
                                          .build();

        return new ReactorClientHttpConnector(
            HttpClient.create()
                      .secure(spec -> spec.sslContext(sslContext))
                      .wiretap(IgnAltitudeService.class.getCanonicalName(), LogLevel.TRACE, AdvancedByteBufFormat.TEXTUAL)
        );
    }

    @Bean
    public AirportProximityService proximityService(
        AltitudeService altitudeService,
        VacCrudRepository vacRepository,
        HvacCrudRepository hvacRepository,
        VacDataCrudRepository dataRepository,
        AirportProximityProperties properties
    ){
        return new AirportProximityService(altitudeService, vacRepository, hvacRepository, dataRepository, properties);
    }
}
