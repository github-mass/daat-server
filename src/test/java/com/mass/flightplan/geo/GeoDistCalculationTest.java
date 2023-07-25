package com.mass.flightplan.geo;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.data.Offset;
import org.assertj.core.data.Percentage;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.measure.Units;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.geometry.coordinate.Position;
import org.opengis.referencing.operation.TransformException;
import si.uom.NonSI;

import javax.measure.UnitConverter;
import java.awt.geom.Point2D;
import java.util.Random;
import java.util.stream.Stream;

import static com.mass.flightplan.model.ModelUtils.latToDecimal;
import static com.mass.flightplan.model.ModelUtils.lonToDecimal;
import static java.lang.Math.*;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class GeoDistCalculationTest {

    @Test
    @Disabled
    public void calculateDist()
        throws TransformException
    {
        GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();

        Position lfqv = new DirectPosition2D(4.64277777777778, 49.785);

        Position mission = new DirectPosition2D(4.680044, 49.78531);

        GeodeticCalculator calc = new GeodeticCalculator();

        calc.setStartingPosition(lfqv);
        calc.setDestinationPosition(mission);

        double azimuthLfqvToMission = calc.getAzimuth();
        double distanceLfqvToMission = calc.getOrthodromicDistance(); //meters

        System.out.printf("Azimuth: %s%n", azimuthLfqvToMission);
        System.out.printf("Orthodromic distance: %s%n", distanceLfqvToMission);

        final long[] runwayQfus = {110L, 290L};

        /*
            Imagine a triangle:
              A is the airport ref point
              B is the mission point
              C is the height on the RWY axis line
              c is distance between A and B / R (R=earth radius)
              a is the DA
              b is the distance of the DA from the airport ref point

              Angle A = azimuth(RWY) - azimuth(airport, mission))

              Then
                tan(b) = cos(A) * tan(c)
                sin(a) = sin(A) * sin(c)
         */

        UnitConverter degreesToRadians = Units.DEGREE_ANGLE.getConverterTo(Units.RADIAN);

        for(int ii = 0; ii < runwayQfus.length; ii++) {
            double A = abs(degreesToRadians.convert(runwayQfus[ii] - azimuthLfqvToMission));
            double c = distanceLfqvToMission / DefaultEllipsoid.WGS84.getSemiMajorAxis();

            double b = atan(cos(A) * tan(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();
            double a = asin(sin(A) * sin(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();

            System.out.printf(
                "For runway %d with qfu %d, got DA=%fm and dist=%fm%n",
                ii, runwayQfus[ii], a, b
            );
        }
    }

    @Test
    void pathTest()
        throws TransformException
    {
        Position p1 = new DirectPosition2D(lonToDecimal("0002324.26E"), latToDecimal("435429.17N"));
        Position p2 = new DirectPosition2D(lonToDecimal("0002249.10E"), latToDecimal("435439.41N"));

        GeodeticCalculator gc = new GeodeticCalculator();
        gc.setStartingPosition(p1);
        gc.setDestinationPosition(p2);

        log.atInfo().addKeyValue("start", p1).addKeyValue("end", p2).log("azimuth: {}", gc.getAzimuth() + 180);
        log.atInfo().addKeyValue("start", p1).addKeyValue("end", p2).log("distance: {}", gc.getOrthodromicDistance());

        var points = gc.getGeodeticPath(1);

        log.atInfo().log("Points: {}", points);
    }

    @Test
    void circleArcTestJTS()
        throws TransformException
    {
        /*
                    <geoLat>460441.00N</geoLat>
                    <geoLong>0044022.00E</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <geoLatArc>454444.00N</geoLatArc>
                    <geoLongArc>0050526.00E</geoLongArc>
                    <valRadiusArc>26.5</valRadiusArc>
                    <uomRadiusArc>NM</uomRadiusArc>

         */

        Coordinate c1 = new Coordinate(lonToDecimal("0044022.00E"), latToDecimal("460441.00N"));
        Coordinate c2 = new Coordinate(lonToDecimal("0050526.00E"), latToDecimal("454444.00N"));

        double d = JTS.orthodromicDistance(c1, c2, DefaultGeographicCRS.WGS84);

        assertThat(d).isCloseTo(NonSI.NAUTICAL_MILE.getConverterTo(Units.METRE).convert(26.5), Percentage.withPercentage(1));
    }

    @Test
    void circleArcTestGeodetic()
        throws TransformException
    {
        /*
                    <geoLat>460441.00N</geoLat>
                    <geoLong>0044022.00E</geoLong>
                    <codeDatum>WGE</codeDatum>
                    <geoLatArc>454444.00N</geoLatArc>
                    <geoLongArc>0050526.00E</geoLongArc>
                    <valRadiusArc>26.5</valRadiusArc>
                    <uomRadiusArc>NM</uomRadiusArc>

         */

        Coordinate c1 = new Coordinate(lonToDecimal("0044022.00E"), latToDecimal("460441.00N"));
        Coordinate c2 = new Coordinate(lonToDecimal("0050526.00E"), latToDecimal("454444.00N"));

        GeodeticCalculator calc = new GeodeticCalculator();

        calc.setStartingPosition(new DirectPosition2D(c1.x, c1.y));
        calc.setDestinationPosition(new DirectPosition2D(c2.x, c2.y));

        double d = calc.getOrthodromicDistance();

        assertThat(d).isCloseTo(NonSI.NAUTICAL_MILE.getConverterTo(Units.METRE).convert(26.5), Percentage.withPercentage(1));
    }

    @ParameterizedTest
    @MethodSource("relativeAzimuthValues")
    void azimuthRelativeToRunwayTest(Point2D location, Point2D runway)
    {
        /*
            Here we check that our azimuthRelativeToRunway value (which we want to represent
            the deviation from the runway axis of the mission point) stays the same regardless
            of which direction we consider the runway in.
         */

        double rwyBrg = new Random().nextDouble() * 360;

        GeodeticCalculator calc = new GeodeticCalculator();
        assertThat(azimuthRelativeToRunway(runway, location, rwyBrg, calc))
            .isEqualTo(azimuthRelativeToRunway(runway, location, (rwyBrg + 180) % 360, calc), Offset.offset(.000000000001));
    }

    static Stream<Arguments> relativeAzimuthValues() {
        return Stream.of(
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.442654, 46.543352)),
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.442654, 46.543352)),
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.442654, 46.543352)),

            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.408864, 46.538024)),
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.408864, 46.538024)),
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.408864, 46.538024)),

            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.426864, 46.527198)),
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.426864, 46.527198)),
            Arguments.of(new DirectPosition2D(3.424213, 46.534641), new DirectPosition2D(3.426864, 46.527198))
        );
    }

    static double azimuthToBearing(double az){
        return az < 0 ? az + 360 : az;
    }

    static double azimuthRelativeToRunway(Point2D location, Point2D runway, double runwayBearing, GeodeticCalculator calc)
    {
        calc.setStartingGeographicPoint(runway);
        calc.setDestinationGeographicPoint(location);
        double az = calc.getAzimuth();

        double relAz = az - runwayBearing;
        while(relAz > 90) relAz -= 180;
        while(relAz < -90) relAz += 180;

        return relAz;
    }

    @Test
    @Disabled
    void triangulationTest()
    {
        Point2D rwy = new Point2D.Double(2.395576, 48.726238);
        Point2D loc1 = new Point2D.Double(2.494509, 48.748239);
        Point2D loc2 = new Point2D.Double(2.393799, 48.714783);
        Point2D loc3 = new Point2D.Double(2.360504, 48.721619);

        final GeodeticCalculator geoCalc = new GeodeticCalculator();
        final UnitConverter degreesToRadians = Units.DEGREE_ANGLE.getConverterTo(Units.RADIAN);

        geoCalc.setStartingGeographicPoint(rwy);
        geoCalc.setDestinationGeographicPoint(loc1);
        double distToLocation = geoCalc.getOrthodromicDistance();

        double rwyBrg = 250;

//        double deg_A = rwyBrg - azimuthToBearing(geoCalc.getAzimuth());
//        double A = degreesToRadians.convert(deg_A);

        double qte = azimuthRelativeToRunway(loc1, rwy, rwyBrg, geoCalc);
        double A = abs(degreesToRadians.convert(qte));

        double c = distToLocation / DefaultEllipsoid.WGS84.getSemiMajorAxis();

        double a = asin(sin(A) * sin(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();
        double b = atan(cos(A) * tan(c)) * DefaultEllipsoid.WGS84.getSemiMajorAxis();

        System.out.printf("DtA: %f%n", a);
        System.out.printf("DoA: %f%n", b);
    }
}
