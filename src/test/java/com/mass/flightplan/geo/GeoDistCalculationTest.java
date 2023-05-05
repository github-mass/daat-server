package com.mass.flightplan.geo;

import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.measure.Units;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.geometry.coordinate.Position;
import org.opengis.referencing.operation.TransformException;

import javax.measure.UnitConverter;

import static java.lang.Math.*;

public class GeoDistCalculationTest {

    @Test
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

}
