package com.mass.flightplan.geo;

import com.mass.flightplan.db.*;
import com.mass.flightplan.vac.RunwayInfo;
import lombok.RequiredArgsConstructor;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.measure.Units;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.opengis.referencing.operation.TransformException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.lang.NonNull;

import javax.measure.UnitConverter;
import javax.measure.quantity.Length;
import java.util.List;

import static java.lang.Math.*;

@RequiredArgsConstructor
public class AirportProximityService {

    private final AltitudeService altitudeService;
    private final VacCrudRepository vacRepository;
    private final HvacCrudRepository hvacRepository;
    private final VacDataCrudRepository dataRepository;
    private final AirportProximityProperties properties;

    public AirportProximityResponse computeFor(@NonNull Point location) {
        try {
            return tryComputeFor(location);
        }
        catch (Exception x) {
            throw new RuntimeException("Could not compute airfield proximities for " + location, x);
        }
    }

    private AirportProximityResponse tryComputeFor(@NonNull Point location)
        throws TransformException
    {
        /*
            Query altitude.

            Load proximate helipads, compute DC for each.

            Load proximate airports, compute DA and DDA for each runway.
         */

        var responseBuilder = AirportProximityResponse.builder().location(location);

        Length alt = altitudeService.getAltitudeAt(location);

        responseBuilder.altitude(alt.to(Units.METRE).getValue().doubleValue());

        final GeodeticCalculator gc = new GeodeticCalculator();
        final UnitConverter degreesToRadians = Units.DEGREE_ANGLE.getConverterTo(Units.RADIAN);

        List<HelipadEntity> helipads = hvacRepository.findByCoordinatesNear(
            location, new Distance(properties.getHelipadMaxDistanceKM(), Metrics.KILOMETERS)
        );
        for (HelipadEntity he : helipads) {
            var ph = AirportProximityResponse.ProximateHelipad.builder()
                                                              .altitude(he.altitude().to(Units.METRE).getValue().doubleValue())
                                                              .code(he.code())
                                                              .coordinates(he.coordinates())
                                                              .name(he.name())
                                                              .qfe(he.localPressure())
                                                              .contact(he.contactInfo())
                                                              .updated(he.updated());

            ph.vacUrl(
                dataRepository.findByCodeEqualsAndTypeEquals(he.code(), VacDataEntity.Type.HVAC)
                              .map(VacDataCrudRepository.UrlView::getUrl)
                              .orElse(null)
            );

            gc.setStartingPosition(new DirectPosition2D(location.getX(), location.getY()));
            gc.setDestinationPosition(new DirectPosition2D(he.coordinates().getX(), he.coordinates().getY()));
            ph.dc(gc.getOrthodromicDistance() / 1000d); // metres to kilometres

            responseBuilder.proximateHelipad(ph.build());
        }

        List<AirportEntity> airports = vacRepository.findByCoordinatesNear(
            location, new Distance(properties.getAirportMaxDistanceKM(), Metrics.KILOMETERS)
        );
        for (AirportEntity ae : airports) {
            var pa = AirportProximityResponse.ProximateAirports.builder()
                                                               .code(ae.code())
                                                               .name(ae.name())
                                                               .qfe(ae.localPressure())
                                                               .altitude(ae.altitude().to(Units.METRE).getValue().doubleValue())
                                                               .coordinates(ae.coordinates())
                                                               .updated(ae.updated())
                                                               .contact(ae.contactInfo())
                                                               .runwaysOk(ae.runwaysOk());

            pa.vacUrl(
                dataRepository.findByCodeEqualsAndTypeEquals(ae.code(), VacDataEntity.Type.VAC)
                              .map(VacDataCrudRepository.UrlView::getUrl)
                              .orElse(null)
            );

            gc.setStartingPosition(new DirectPosition2D(location.getX(), location.getY()));
            gc.setDestinationPosition(new DirectPosition2D(ae.coordinates().getX(), ae.coordinates().getY()));

            double airportAzimuthToLocation = gc.getAzimuth();
            double airportDistanceToLocation = gc.getOrthodromicDistance(); //metres

            pa.distance(airportDistanceToLocation / 1000d); // metres to KM.

            for (RunwayInfo rwy : ae.runways()) {
                /*
                    Imagine a triangle:
                      A is the airport ref point
                      B is the mission point
                      C is the height on the RWY axis line
                      c is distance between A and B / R (R=earth radius)
                      a is the DA
                      b is the distance of the DA from the airport ref point

                      Angle A = azimuth(RWY) - azimuth(airport, mission))

                      For azimuth(RWY), we'll use QFU + airport_magnetic_declination (must make sure take magnetic
                      declination into account, since we're looking for "true" measurements).

                      Then (Napier's rules for right spherical triangles)
                        tan(b) = cos(A) * tan(c)
                        sin(a) = sin(A) * sin(c)
                 */
                /*
                    Here,
                 */
                double A = abs(degreesToRadians.convert(rwy.qfu() + ae.magneticDeclination() - airportAzimuthToLocation));
                double c = airportDistanceToLocation / DefaultEllipsoid.WGS84.getSemiMajorAxis();

                double a = asin(sin(A) * sin(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();
                double b = atan(cos(A) * tan(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();

                var pr = AirportProximityResponse.ProximateRunway.builder()
                                                                 .runway(rwy.code())
                                                                 .qfu(rwy.qfu())
                                                                 .length(rwy.length())
                                                                 .paved(rwy.paved())
                                                                 .distToAxis(abs(a) / 1000d) // m to km
                                                                 .distOnAxis(abs(b) / 1000d); // m to km

                pa.runway(pr.build());
            }

            responseBuilder.proximateAirport(pa.build());
        }

        return responseBuilder.build();
    }

}
