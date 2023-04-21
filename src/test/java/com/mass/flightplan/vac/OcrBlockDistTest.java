package com.mass.flightplan.vac;

import org.assertj.core.data.Offset;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

class OcrBlockDistTest {

    @ParameterizedTest
    @MethodSource("vDistParams")
    void vDist(OcrBlock block1, OcrBlock block2, double expected, double precision) {
        assertThat(block1.vDist(block2))
            .as("Verify that vDistance matches expected value between %s and %s", block1, block2)
            .isCloseTo(expected, within(precision));

        assertThat(block2.vDist(block1))
            .as("Verify that distance is reflexive between %s and %s", block1, block2)
            .isEqualTo(block1.vDist(block2));

        assertThat(block1.vDist(block1))
            .as("Verify that self-distance is -1 for %s", block1)
            .isEqualTo(-1);
    }

    private static List<Arguments> vDistParams() {
        return List.of(
            Arguments.of(block(120, 10, 200, 10), block(0, 0, 20, 0), .5, 0),
            Arguments.of(block(10, 50, 50, 50), block(150, 0, 310, 42), .33, 0.01),
            Arguments.of(block(0, 50, 100, 50), block(50, 0, 200, 5), -0.25, 0),
            Arguments.of(block(0, 50, 100, 50), block(100, 0, 200, 5), 0, 0),
            Arguments.of(block(35, 50, 85, 50), block(35, 0, 85, 15), -1, 0)
        );
    }

    @Test
    void hDist() {
    }

    static OcrBlock block(double top, double left, double bottom, double right) {
        return OcrBlock.builder().page(0).top(top).left(left).bottom(bottom).right(right).text("").build();
    }

    @Test
    void dbg(){
        OcrBlock b1 = block(.617944, .174732, .632392, .915375),
            b2 = block(.618448, .142312, .629704, .162098)
        ;
        System.out.println("VDist: " + b1.vDist(b2));
        System.out.println("HDist: " + b1.hDist(b2));

    }
}