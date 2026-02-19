package com.mass.daat.model.aixm;

import com.mass.daat.model.ModelUtils;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ModelUtilsTest {

    @Test
    void testFlightLevel(){
        Quantity<Length> ln = Quantities.getQuantity(80, ModelUtils.parseLengthUnit("FL_STD"));
        assertThat(ln.to(Units.METRE).getValue().doubleValue()).isCloseTo(80 * 30.48, Offset.offset(1d));
        assertThat(ln.toString()).isEqualTo("80 FL_STD");
        System.out.println(ln);
    }

    @ParameterizedTest
    @MethodSource("latToDecimalArguments")
    void latToDecimal(String latString, double expected) {
        assertThat(ModelUtils.latToDecimal(latString))
            .as("Decimal representation of AIXM latitude %s", latString)
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("lonToDecimalArguments")
    void lonToDecimal(String lonString, double expected) {
        assertThat(ModelUtils.lonToDecimal(lonString))
            .as("Decimal representation of AIXM longitude %s", lonString)
            .isEqualTo(expected);
    }


    static Stream<Arguments> latToDecimalArguments(){
        return Stream.of(
            Arguments.of("435429.17N", 43 + 54 / 60d + 29.17 / 3600d),
            Arguments.of("483639.29N", 48 + 36 / 60d + 39.29 / 3600d),
            Arguments.of("144231.74S", -(14 + 42 / 60d + 31.74 / 3600d)),
            Arguments.of("414633N", 41 + 46 / 60d + 33 / 3600d),
            Arguments.of("435337.00N", 43 + 53 / 60d + 37 / 3600d),
            Arguments.of("4332.4906N", 43 + 32.4906 / 60d),
            Arguments.of("12.049316S", -(12 + 0.049316)),

            Arguments.of("900000.00N", 90),
            Arguments.of("9000.0000N", 90),
            Arguments.of("90.000000N", 90),
            Arguments.of("900000N", 90),
            Arguments.of("9000N", 90),
            Arguments.of("90N", 90),
            Arguments.of("900000.00S", -90),
            Arguments.of("9000.0000S", -90),
            Arguments.of("90.000000S", -90),
            Arguments.of("900000S", -90),
            Arguments.of("9000S", -90),
            Arguments.of("90S", -90)
        );
    }

    static Stream<Arguments> lonToDecimalArguments(){
        return Stream.of(
            Arguments.of("0002324.26E", 0 + 23 / 60d + 24.26 / 3600d),
            Arguments.of("0030053.83E", 3 + 0 / 60d + 53.83 / 3600d),
            Arguments.of("1451429.84W", -(145 + 14 / 60d + 29.84 / 3600d)),
            Arguments.of("0130658.32W", -(13 + 6 / 60d + 58.32 / 3600d)),
            Arguments.of("0083411W", -(8 + 34 / 60d + 11 / 3600d)),
            Arguments.of("0060006.00W", -(6 + 0 + 6 / 3600d)),
            Arguments.of("00140.58W", -(1 + 40.58 / 60d)),
            Arguments.of("045.092305E", 45 + .092305),

            Arguments.of("1800000.00E", 180),
            Arguments.of("18000.0000E", 180),
            Arguments.of("180.000000E", 180),
            Arguments.of("1800000E", 180),
            Arguments.of("18000E", 180),
            Arguments.of("180E", 180),
            Arguments.of("1800000.00W", -180),
            Arguments.of("18000.0000W", -180),
            Arguments.of("180.000000W", -180),
            Arguments.of("1800000W", -180),
            Arguments.of("18000W", -180),
            Arguments.of("180W", -180)

        );
    }

    @Test
    void testLeadingZeros(){
        assertThat(Double.parseDouble("0000045678")).isEqualTo(45678d);
    }
}