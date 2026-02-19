package com.mass.daat.geo;

import lombok.*;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.log4j.Log4j2;
import org.geotools.measure.Units;
import org.springframework.data.geo.Point;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.time.Duration;
import java.util.List;

import static java.util.function.Predicate.not;

@Log4j2
@RequiredArgsConstructor
public class IgnAltitudeService
    implements AltitudeService
{
    private final @NonNull WebClient webClient;

    private final @NonNull AltitudeServiceProperties.IgnServiceProperties properties;

    @SneakyThrows
    @Override
    public Quantity<Length> getAltitudeAt(Point coordinate)
    {
        return getAltitudeAt(coordinate, true);
    }

    private Quantity<Length> getAltitudeAt(Point coordinate, boolean retryOn429)
            throws InterruptedException
    {
        try {
            var alt = queryAltitude(coordinate);

            return Quantities.getQuantity(alt.z(), Units.METRE);
        }
        catch (RuntimeException rcex) {
            if(retryOn429 && rcex instanceof WebClientResponseException.TooManyRequests){
                Duration wait = Duration.ofMillis(2500);
                log.warn("Received 429 TOO_MANY_REQUESTS from IGN altitude service. Waiting {}ms and retrying...", wait.toMillis());
                Thread.sleep(wait.toMillis());
                return getAltitudeAt(coordinate, false);
            }

            throw new IllegalStateException(
                String.format("Could not retrieve altitude using IGN service for coordinates lat=%s, lon=%s", coordinate.getY(), coordinate.getX()), rcex
            );
        }
    }

    private IgnAltitude queryAltitude(Point c)
        throws WebClientResponseException
    {
        log.debug("Querying altitude using IGN service for: lat={}, lon={}", c.getY(), c.getX());

        var resp = webClient
            .get()
            .uri(
                properties.getRestServiceUrl(),
                b -> b.queryParam("lat", c.getY()).queryParam("lon", c.getX())
                      .queryParam("resource", properties.getDataResourceId())
                      .build())
            .retrieve()
            .onStatus(not(HttpStatusCode::is2xxSuccessful), ClientResponse::createException)
            .bodyToMono(IgnAltitudeResponse.class)
            .block();

        if (resp == null || resp.elevations().isEmpty()) {
            throw new IllegalArgumentException("Invalid response from IGN service: " + resp);
        }
        else {
            log.debug("Got altitude response: {}", resp.elevations().get(0));
            return resp.elevations().get(0);
        }
    }

    @Value
    @Jacksonized
    @Builder
    static class IgnAltitudeResponse {
        List<IgnAltitude> elevations;
    }

    @Value
    @Jacksonized
    @Builder
    @ToString
    static class IgnAltitude {
        double lon;
        double lat;
        double z;
        String acc;
    }
}
