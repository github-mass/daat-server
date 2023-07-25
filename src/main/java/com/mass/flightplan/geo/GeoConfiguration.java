package com.mass.flightplan.geo;

import com.mass.flightplan.db.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import javax.net.ssl.SSLException;

@Configuration
@RequiredArgsConstructor
@Import(WebClientAutoConfiguration.class)
@Slf4j
public class GeoConfiguration {

    @Bean
    public AltitudeServiceProperties altitudeServiceProperties(){
        return new AltitudeServiceProperties();
    }

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
    @Primary
    @ConditionalOnProperty(name = "cache.altitude.enabled")
    public AltitudeService AltitudeServiceCachingPostProcessor(@NonNull AltitudeService delegate){
        log.info("Using caching version of AltitudeService");
        return new CachingAltitudeService(delegate);
    }

    @Bean
    @ConfigurationProperties(prefix = "proximity-service")
    public ProximityServiceProperties proximityServiceProperties(){
        return new ProximityServiceProperties();
    }

    @Bean
    public ProximityService proximityService(
        ProximityServiceProperties properties,
        AltitudeService altitudeService,
        DatasetRepository datasetRepo,
        AerodromeRepository adRepo,
        HeliportRepository hpRepo,
        AirspaceRepository asRepo,
        ZicadRepository zicadRepo
    ){
        return new ProximityService(altitudeService, datasetRepo, adRepo, hpRepo, asRepo, zicadRepo, properties);
    }
}
