package com.mass.daat.model;

import lombok.experimental.UtilityClass;
import org.geotools.measure.Units;
import org.geotools.referencing.GeodeticCalculator;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import si.uom.SI;
import tech.units.indriya.function.AbstractConverter;
import tech.units.indriya.function.RationalConverter;
import tech.units.indriya.unit.TransformedUnit;

import javax.measure.Unit;
import javax.measure.UnitConverter;
import javax.measure.quantity.Length;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.PI;

@UtilityClass
public class ModelUtils {

    // Pattern taken from XSD
    private static final Pattern AIXM_LAT_PATTERN = Pattern.compile(
        "([0-8][0-9]°?[0-5][0-9]'?[0-5][0-9](\\.\\d{1,4})?\"?([NS]))|([0-8][0-9]°?[0-5][0-9](\\.\\d{1,8})?'?([NS]))|([0-8][0-9](\\.\\d{1,8})?°?([NS]))|(900000(\\.0{1,4})?°?([NS]))|(9000(\\.0{1,8})?°?([NS]))|(90(\\.0{1,8})?°?([NS]))"
    );
    private static final Pattern AIXM_LON_PATTERN = Pattern.compile(
        "(((0[0-9])|(1[0-7]))[0-9]°?[0-5][0-9]'?[0-5][0-9](\\.\\d{1,4})?\"?([EW]))|(((0[0-9])|(1[0-7]))[0-9]°?[0-5][0-9](\\.\\d{1,8})?'?([EW]))|(((0[0-9])|(1[0-7]))[0-9](\\.\\d{1,8})?°?([EW]))|(1800000(\\.0{1,4})?°?([EW]))|(18000(\\.0{1,8})?°?([EW]))|(180(\\.0{1,8})?°?([EW]))"
    );

    public static double latToDecimal(String lat) {
        Matcher m = AIXM_LAT_PATTERN.matcher(lat);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }

        String match = m.group().replaceAll("[°'\"]", "");

        final int integralLength = match.indexOf('.') == -1 ? match.length() - 1 : match.indexOf('.');

        // integral length is 2, 4 or 6
        double accumulator = switch (integralLength) {
            case 2 -> Double.parseDouble(match.substring(0, match.length() - 1));

            case 4 -> Double.parseDouble(match.substring(0, 2))
                + Double.parseDouble(match.substring(2, match.length() - 1)) / 60;

            case 6 -> Double.parseDouble(match.substring(0, 2))
                + Double.parseDouble(match.substring(2, 4)) / 60
                + Double.parseDouble(match.substring(4, match.length() - 1)) / 3600;

            default -> throw new IllegalArgumentException("Invalid latitude: " + lat);
        };

        if (match.charAt(match.length() - 1) == 'S') {
            accumulator = -accumulator;
        }

        return accumulator;
    }

    public static double lonToDecimal(String lon) {
        Matcher m = AIXM_LON_PATTERN.matcher(lon);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid longitude: " + lon);
        }

        String match = m.group().replaceAll("[°'\"]", "");

        //always min three digits for the integer part

        final int integralLength = match.indexOf('.') == -1 ? match.length() - 1 : match.indexOf('.');

        // integral length is 3, 5 or 7
        double accumulator = switch (integralLength) {
            case 3 -> Double.parseDouble(match.substring(0, match.length() - 1));

            case 5 -> Double.parseDouble(match.substring(0, 3))
                + Double.parseDouble(match.substring(3, match.length() - 1)) / 60;

            case 7 -> Double.parseDouble(match.substring(0, 3))
                + Double.parseDouble(match.substring(3, 5)) / 60
                + Double.parseDouble(match.substring(5, match.length() - 1)) / 3600;

            default -> throw new IllegalArgumentException("Invalid longitude: " + lon);
        };

        if (match.charAt(match.length() - 1) == 'W') {
            accumulator = -accumulator;
        }

        return accumulator;
    }

    @NonNull
    public static Unit<Length> parseLengthUnit(@NonNull String unit){
        return switch(unit){
            case "M" -> SI.METRE;
            case "KM" -> Units.KILOMETER;
            case "NM" -> Units.NAUTICAL_MILE;
            case "FT" -> Units.FOOT;
            case "FT_HEI", "ft ASFC" -> FEET_HEIGHT;
            case "FT_ALT", "ft AMSL" -> FEET_AMSL;
            case "FL_STD", "FL" -> FLIGHT_LEVEL;
            default -> throw new IllegalArgumentException("Unknown unit: " + unit);
        };
    }

    public static final Unit<Length> FLIGHT_LEVEL, FEET_HEIGHT, FEET_AMSL;

    private static final AbstractConverter METRE_TO_FL = new AbstractConverter() {
        @Override
        public boolean equals(Object o) {
            return o == this;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public AbstractConverter inverseWhenNotIdentity() {
            return FL_TO_METRE;
        }

        @Override
        protected String transformationLiteral() {
            return "%s m -> FL %s";
        }

        @Override
        protected boolean canReduceWith(AbstractConverter that) {
            if (that instanceof RationalConverter) {
                return true;
            }

            return false;
        }

        @Override
        protected Number convertWhenNotIdentity(Number value) {
            // M = FT * 0.3048 <=> FT = M / 0.3048
            // FL = round(FT / 100)
            // FL = round(M / 0.3048 / 100)
            return Math.round(value.doubleValue() / 30.48);
        }

        @Override
        public int compareTo(@NotNull UnitConverter o) {
            return this.hashCode() - o.hashCode();
        }

        @Override
        public boolean isIdentity() {
            return false;
        }

        @Override
        public boolean isLinear() {
            return false;
        }
    };

    private static final AbstractConverter FL_TO_METRE = new AbstractConverter() {
        @Override
        public int compareTo(@NotNull UnitConverter o) {
            return this.hashCode() - o.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        protected String transformationLiteral() {
            return "FL %s -> %s m";
        }

        @Override
        public AbstractConverter inverseWhenNotIdentity() {
            return METRE_TO_FL;
        }

        @Override
        protected boolean canReduceWith(AbstractConverter that) {
            if (that instanceof RationalConverter) {
                return true;
            }

            return false;
        }

        @Override
        protected Number convertWhenNotIdentity(Number value) {
            // M = FT * 0.3048
            // FL = round(FT / 100) <=> FT = FL * 100
            // M = FL * 100 * 0.3048
            return value.doubleValue() * 30.48;
        }

        @Override
        public boolean isIdentity() {
            return false;
        }

        @Override
        public boolean isLinear() {
            return false;
        }
    };

    static {
        Unit<Length> fl, ft_hei, ft_alt;
        try {
            fl = new TransformedUnit<>("FL_STD", SI.METRE, FL_TO_METRE);
            ft_hei = new TransformedUnit<>( "FT_HEI", Units.FOOT, RationalConverter.IDENTITY);
            ft_alt = new TransformedUnit<>( "FT_ALT", Units.FOOT, RationalConverter.IDENTITY);
        }
        catch (Exception x) {
            x.printStackTrace();
            throw new ExceptionInInitializerError(x);
        }

        FLIGHT_LEVEL = fl;
        FEET_HEIGHT = ft_hei;
        FEET_AMSL = ft_alt;
    }

    /**
     * Azimuth as returned by {@link GeodeticCalculator#getAzimuth()} to radians.
     */
    public static double az2Rad(double az){
        while(az < 0) az += 360;
        return az / 180 * PI;
    }

    /**
     * Radians to azimuth as understood by {@link GeodeticCalculator}.
     */
    public static double rad2Az(double rad){
        rad = rad / PI;
        rad %= 2;
        if(rad > 1) rad -= 2;
        return rad * 180;
    }

    public static boolean isPaved(@Nullable String rwyComposition){
        /*
	<xsd:simpleType name="codeCompositionSfcBase">
		<xsd:restriction base="xsd:string">
			<xsd:enumeration value="ASPH"/>
			<xsd:enumeration value="ASP+GRS"/>
			<xsd:enumeration value="CONC"/>
			<xsd:enumeration value="CONC+ASPH"/>
			<xsd:enumeration value="CONC+GRS"/>
			<xsd:enumeration value="GRASS"/>
			<xsd:enumeration value="SAND"/>
			<xsd:enumeration value="WATER"/>
			<xsd:enumeration value="BITUM"/>
			<xsd:enumeration value="BRICK"/>
			<xsd:enumeration value="MACADAM"/>
			<xsd:enumeration value="STONE"/>
			<xsd:enumeration value="CORAL"/>
			<xsd:enumeration value="CLAY"/>
			<xsd:enumeration value="LATERITE"/>
			<xsd:enumeration value="GRADE"/>
			<xsd:enumeration value="GRAVE"/>
			<xsd:enumeration value="ICE"/>
			<xsd:enumeration value="SNOW"/>
			<xsd:enumeration value="MEMBRANE"/>
			<xsd:enumeration value="METAL"/>
			<xsd:enumeration value="MATS"/>
			<xsd:enumeration value="PSP"/>
			<xsd:enumeration value="WOOD"/>
			<xsd:enumeration value="OTHER"/>
		</xsd:restriction>
	</xsd:simpleType>
         */

        return rwyComposition != null && PAVED_COMPOSITION_TYPES.contains(rwyComposition);
    }

    static final List<String> PAVED_COMPOSITION_TYPES = List.of(
        "ASP+GRS",
        "ASPH",
        "CONC",
        "CONC+ASPH",
        "CONC+GRS",
        "BITUM",
        "BRICK",
        "MACADAM",
        "STONE",
        "LATERITE",
        "GRADE",
        "METAL",
        "PSP", //?
        "WOOD" //?
    );
    /*
        Exclude:
        "CORAL",
        "CLAY",
        "GRASS",
        "GRAVE",
        "ICE",
        "MATS",
        "MEMBRANE",
        "SAND",
        "SNOW",
        "WATER",
        "OTHER",
     */

}
