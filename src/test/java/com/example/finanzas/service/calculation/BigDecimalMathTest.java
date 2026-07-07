package com.example.finanzas.service.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BigDecimalMathTest {

    @Test
    void pow_computesCorrectResult() {
        BigDecimal base = BigDecimalMath.of("1.12");
        BigDecimal result = BigDecimalMath.pow(base, BigDecimalMath.of("0.0833333333"));
        assertEquals(0, result.compareTo(BigDecimalMath.of("1.0094887929")));
    }

    @Test
    void pow_supportsNegativeIntegerExponent() {
        BigDecimal result = BigDecimalMath.pow(BigDecimalMath.of("1.01"), -2);
        assertEquals(0, result.compareTo(BigDecimalMath.of("0.9802960494")));
    }

    @Test
    void divide_maintainsScale() {
        BigDecimal result = BigDecimalMath.divide(BigDecimalMath.of("1"), BigDecimalMath.of("12"));
        assertEquals(10, result.scale());
    }

    @Test
    void scaleOutput_roundsToTwoDecimals() {
        BigDecimal result = BigDecimalMath.scaleOutput(BigDecimalMath.of("123.456789"));
        assertEquals(2, result.scale());
        assertEquals(0, result.compareTo(new BigDecimal("123.46")));
    }
}
