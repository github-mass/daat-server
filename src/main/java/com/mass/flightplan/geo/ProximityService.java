package com.mass.flightplan.geo;

import com.mass.flightplan.db.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.measure.Units;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.lang.NonNull;
import si.uom.SI;

import javax.measure.Quantity;
import javax.measure.UnitConverter;
import javax.measure.quantity.Length;
import java.awt.geom.Point2D;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.*;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toList;

@Slf4j
@RequiredArgsConstructor
public class ProximityService {

    private final AltitudeService altitudeService;
    private final DatasetRepository datasetRepo;
    private final AerodromeRepository adRepo;
    private final HeliportRepository hpRepo;
    private final AirspaceRepository asRepo;
    private final ZicadRepository zicadRepo;
    private final ProximityServiceProperties properties;

    public ProximityResponse computeFor(@NonNull Point location) {
        try {
            var responseBuilder = ProximityResponse.builder().location(location);

            Quantity<Length> alt = altitudeService.getAltitudeAt(location);
            responseBuilder.altitudeM(alt.to(Units.METRE).getValue().doubleValue());

            tryComputeAixmFor(location, responseBuilder);
            tryComputeZicadFor(location, responseBuilder);

            return responseBuilder.build();
        }
        catch (Exception x) {
            throw new RuntimeException("Could not compute proximity response for " + location, x);
        }
    }

    private void tryComputeAixmFor(@NonNull Point queryLocation, ProximityResponse.ProximityResponseBuilder responseBuilder) {

        DatasetEntity dataset = datasetRepo.currentAixm();

        if (dataset == null || dataset.effective().isAfter(now())) {
            log.warn("No current ZICAD dataset available ({})", dataset);
            return ;
        }

        var ds = ProximityResponse.DatasetInfo.builder();
        ds.source(dataset.datasetType()).effective(dataset.effective());
        responseBuilder.dataset(ds.build());

        final GeodeticCalculator geoCalc = new GeodeticCalculator();
        final UnitConverter degreesToRadians = Units.DEGREE_ANGLE.getConverterTo(Units.RADIAN);

        List<HeliportEntity> heliports = hpRepo.findByDatasetAndCoordinatesNear(
            dataset, new GeoJsonPoint(queryLocation), new Distance(properties.getHelipadMaxDistanceKM(), Metrics.KILOMETERS)
        );

        for (HeliportEntity he : heliports) {
            var ph = ProximityResponse.ProximateHeliport.builder()
                                                        .altitudeM(he.elevation().to(Units.METRE).getValue().doubleValue())
                                                        .code(he.code())
                                                        .coordinates(he.coordinates())
                                                        .name(he.name())
                                                        .contact(he.contactInfos())
                                                        .admin(he.adminAuthority());

            geoCalc.setStartingGeographicPoint(queryLocation.getX(), queryLocation.getY());
            geoCalc.setDestinationGeographicPoint(he.coordinates().getX(), he.coordinates().getY());
            double minDc = geoCalc.getOrthodromicDistance(); //metres

            // bearing from query location to airfield
            ph.quj(azimuthToBearing(geoCalc.getAzimuth()));

            ph.distanceM(minDc);

            for (TlaEntity tla : he.takeoffLandingAreas()) {
                var tlaBuilder = ProximityResponse.ProximateHeliportTla.builder();

                tlaBuilder.designation(tla.designation()).remark(tla.remark()).composition(tla.composition());
                tla.elevation().map(e -> e.to(SI.METRE).getValue().doubleValue()).ifPresent(tlaBuilder::altitudeM);

                geoCalc.setDestinationGeographicPoint(tla.coordinates().getX(), tla.coordinates().getY());
                double dc = geoCalc.getOrthodromicDistance(); //metres
                tlaBuilder.dcM(dc);

                ph.takeOffLandingArea(tlaBuilder.build());

                minDc = min(dc, minDc);
            }

            ph.minDcInMetres(minDc);

            responseBuilder.proximateHeliport(ph.build());
        }

        /*
            Do airspaces before we do airports; we'll use this to check whether we're in the CTR.
         */
        List<AirspaceEntity> airspaces = asRepo.findByDatasetAndGeometryNear(
            dataset, new GeoJsonPoint(queryLocation), new Distance(properties.getAirspaceMaxDistanceKM(), Metrics.KILOMETERS)
        );

        final Set<BigInteger> insideAirspaces = new HashSet<>();

        for (AirspaceEntity as : airspaces) {
            var pa = ProximityResponse.ProximateAirspace.builder()
                                                        .code(as.code())
                                                        .type(as.type())
                                                        .remark(as.remark())
                                                        .activationType(as.activationType())
                                                        .activationRemark(as.activationRemark())
                                                        .frontiers(as.frontiers())
                                                        .minFloor(as.minFloor())
                                                        .maxCeiling(as.maxCeiling());

            Geometry geom = toGeometry(as.geometry());
            double dist;

            org.locationtech.jts.geom.Point locAsJtsPoint = JTSFactoryFinder
                .getGeometryFactory().createPoint(new Coordinate(queryLocation.getX(), queryLocation.getY()));

            if (geom.contains(locAsJtsPoint)) {
                dist = 0;
                insideAirspaces.add(as.id());
            }
            else {
                Coordinate[] closest = DistanceOp.nearestPoints(geom, locAsJtsPoint);

                geoCalc.setStartingGeographicPoint(closest[0].x, closest[0].y);
                geoCalc.setDestinationGeographicPoint(closest[1].x, closest[1].y);

                dist = geoCalc.getOrthodromicDistance();
            }

            pa.distanceM(dist);

            responseBuilder.proximateAirspace(pa.build());
        }

        List<AerodromeEntity> airports = adRepo.findByDatasetAndCoordinatesNear(
            dataset, new GeoJsonPoint(queryLocation), new Distance(properties.getAirportMaxDistanceKM(), Metrics.KILOMETERS)
        );

        for (AerodromeEntity ae : airports) {
            var pa = ProximityResponse.ProximateAerodrome.builder()
                                                         .code(ae.code())
                                                         .name(ae.name())
                                                         .altitudeM(ae.elevation().to(Units.METRE).getValue().doubleValue())
                                                         .coordinates(ae.coordinates())
                                                         .servedCity(ae.servedCity())
                                                         .siteDescription(ae.siteDescription())
                                                         .adminAuthority(ae.adminAuthority())
                                                         .contact(ae.contactInfos());

            geoCalc.setStartingGeographicPoint(queryLocation.getX(), queryLocation.getY());
            geoCalc.setDestinationGeographicPoint(ae.coordinates().getX(), ae.coordinates().getY());

            pa.distanceM(geoCalc.getOrthodromicDistance());

            // bearing from query location to airfield
            pa.quj(azimuthToBearing(geoCalc.getAzimuth()));

            double minDa = Double.POSITIVE_INFINITY;

            for (RunwayEntity rwy : ae.runways()) {
                /*
                    Imagine a triangle:
                      A is the runway ref point (or airport' s if it has none)
                      B is the mission point
                      C is the height on the RWY axis line
                      c is distance between A and B / R (R=earth radius)
                      a is the DA
                      b is the distance of the DA from the airport ref point

                      Angle A = azimuth(RWY) - azimuth(airport, mission))

                      For azimuth(RWY), we'll use trueBearing

                      Then (Napier's rules for right spherical triangles)
                        tan(b) = cos(A) * tan(c)
                        sin(a) = sin(A) * sin(c)
                 */
                double rwyBrg;
                Point rwyCoord = Optional.ofNullable(rwy.coordinates()).orElse(ae.coordinates());
                if (rwy.trueBearing() != null) {
                    //noinspection ConstantConditions
                    rwyBrg = rwy.trueBearing();
                }
                else if (rwy.magBearing() != null) {
                    //noinspection ConstantConditions
                    rwyBrg = rwy.magBearing() + Optional.ofNullable(ae.magVar()).orElse(0d);
                }
                else {
//                    throw new IllegalStateException("Invalid runway %s has neither true nor mag bearing on AD %s".formatted(rwy.designation(), ae.code()));
                    /*
                        This can happen.
                        We'll have to ignore this runway.
                     */
                    log.warn("Ignoring runway {} for AD {} ({}) as no runway bearing available", rwy.designation(), ae.code(), ae.name());
                    continue;
                }

                geoCalc.setStartingGeographicPoint(rwyCoord.getX(), rwyCoord.getY());
                geoCalc.setDestinationGeographicPoint(queryLocation.getX(), queryLocation.getY());
                double distToLocation = geoCalc.getOrthodromicDistance();

                /*
                    Bearing of query location relative to runway axis.

                    Careful, this affects coordinates in GeodeticCalculator.
                 */
                double qte = azimuthRelativeToRunway(
                    new Point2D.Double(queryLocation.getX(), queryLocation.getY()),
                    new Point2D.Double(rwyCoord.getX(), rwyCoord.getY()),
                    rwyBrg, geoCalc
                );

                double A = abs(degreesToRadians.convert(qte));
                double c = distToLocation / DefaultEllipsoid.WGS84.getSemiMajorAxis();

                double a = asin(sin(A) * sin(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();
                double b = atan(cos(A) * tan(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();

                var pr = ProximityResponse.ProximateRunway.builder()
                                                          .runway(rwy.designation())
                                                          .trueBearing(rwyBrg)
                                                          .lengthM(rwy.length().to(SI.METRE).getValue().doubleValue())
                                                          .widthM(rwy.width().to(SI.METRE).getValue().doubleValue())
                                                          .paved(rwy.paved())
                                                          .composition(rwy.composition())
                                                          .minAltitudeM(rwy.minElevation().orElse(ae.elevation()).to(SI.METRE).getValue().doubleValue())
                                                          .maxAltitudeM(rwy.maxElevation().orElse(ae.elevation()).to(SI.METRE).getValue().doubleValue())
                                                          .coordinates(rwyCoord);

                pr.distToAxisM(abs(a)).distOnAxisM(abs(b));
                pr.azimuthToQuery(qte);

                minDa = min(minDa, abs(a));

                pa.runway(pr.build());
            }

            pa.minDistToAxisM(minDa);

            AirspaceEntity ctr = ae.ctr();
            pa.hasCtr(ctr != null);
            pa.inCtr(ctr != null && insideAirspaces.contains(ctr.id()));

            responseBuilder.proximateAerodrome(pa.build());
        }
    }

    void tryComputeZicadFor(@NonNull Point queryLocation, ProximityResponse.ProximityResponseBuilder responseBuilder){
        DatasetEntity dataset = datasetRepo.currentZicad();

        if (dataset == null || dataset.effective().isAfter(now())) {
            log.warn("No current ZICAD dataset available ({})", dataset);
            return ;
        }

        var ds = ProximityResponse.DatasetInfo.builder();
        ds.source(dataset.datasetType()).effective(dataset.effective());
        responseBuilder.dataset(ds.build());

        final GeodeticCalculator geoCalc = new GeodeticCalculator();
        final UnitConverter degreesToRadians = Units.DEGREE_ANGLE.getConverterTo(Units.RADIAN);
        final GeometryFactory gFact = JTSFactoryFinder.getGeometryFactory();

        List<ZicadEntity> zicads = zicadRepo.findByDatasetAndGeometryNear(
            dataset, new GeoJsonPoint(queryLocation), new Distance(properties.getZicadMaxDistanceKM(), Metrics.KILOMETERS)
        );

        for (ZicadEntity ze: zicads) {
            if(ze.effective().isAfter(now())){
                log.debug("Skipping ZICAD entry as it's not yet effective: {}", ze);
                continue;
            }

            var zb = ProximityResponse.ProximateZicad.builder();
            zb.name(ze.siteName()).areaId(ze.areaId());

            Geometry geom = toGeometry(ze.geometry());
            double dist;

            org.locationtech.jts.geom.Point locAsJtsPoint = gFact.createPoint(new Coordinate(queryLocation.getX(), queryLocation.getY()));

            if (geom.contains(locAsJtsPoint)) {
                dist = 0;
            }
            else {
                Coordinate[] closest = DistanceOp.nearestPoints(geom, locAsJtsPoint);

                geoCalc.setStartingGeographicPoint(closest[0].x, closest[0].y);
                geoCalc.setDestinationGeographicPoint(closest[1].x, closest[1].y);

                dist = geoCalc.getOrthodromicDistance();
            }

            zb.distanceM(dist);

            responseBuilder.proximateZicad(zb.build());
        }
    }

    static Geometry toGeometry(GeoJson<?> geojson) {
        GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
        switch (geojson.getType()) {
            case "Point" -> {
                GeoJsonPoint p = (GeoJsonPoint) geojson;
                return gf.createPoint(new Coordinate(p.getX(), p.getY()));
            }
            case "Polygon" -> {
                GeoJsonPolygon p = (GeoJsonPolygon) geojson;
                return p.getCoordinates().stream()
                        .map(ls ->
                            ls.getCoordinates()
                              .stream()
                              .map(point -> new Coordinate(point.getX(), point.getY()))
                        )
                        .map(stream -> gf.createLinearRing(stream.toArray(Coordinate[]::new)))
                        .collect(Collectors.collectingAndThen(toList(), l -> gf.createPolygon(l.get(0), l.stream().skip(1).toArray(LinearRing[]::new))));
            }
            case "MultiPolygon" -> {
                GeoJsonMultiPolygon p = (GeoJsonMultiPolygon) geojson;
                return p.getCoordinates().stream()
                        .map(ProximityService::toGeometry)
                        .collect(Collectors.collectingAndThen(toList(), gf::buildGeometry));
            }
            default -> throw new IllegalArgumentException("Unexpected GeoJSON: " + geojson);
        }
    }

    /**
     * Convert [-180, 180] azimuth to [0, 360[ bearing.
     */
    static double azimuthToBearing(double az) {
        return az < 0 ? az + 360 : az;
    }

    static double azimuthRelativeToRunway(Point2D location, Point2D runway, double runwayBearing, GeodeticCalculator calc) {
        calc.setStartingGeographicPoint(runway);
        calc.setDestinationGeographicPoint(location);
        double az = calc.getAzimuth();

        double relAz = az - runwayBearing;
        while (relAz > 90) relAz -= 180;
        while (relAz < -90) relAz += 180;

        return relAz;
    }
}
