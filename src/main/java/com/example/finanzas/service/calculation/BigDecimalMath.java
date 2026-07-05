package com.example.finanzas.service.calculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * BigDecimal-only math utilities for financial calculations.
 * All monetary operations use scale 10 and HALF_UP rounding internally.
 */
public final class BigDecimalMath {

    public static final int INTERNAL_SCALE = 10;
    public static final int OUTPUT_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final MathContext MC = new MathContext(20, ROUNDING);

    private BigDecimalMath() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(INTERNAL_SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return new BigDecimal(value).setScale(INTERNAL_SCALE, ROUNDING);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(INTERNAL_SCALE, ROUNDING);
    }

    public static BigDecimal scaleInternal(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(INTERNAL_SCALE, ROUNDING);
    }

    public static BigDecimal scaleOutput(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(OUTPUT_SCALE, ROUNDING);
        }
        return value.setScale(OUTPUT_SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return scaleInternal(a).add(scaleInternal(b), MC);
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return scaleInternal(a).subtract(scaleInternal(b), MC);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return scaleInternal(a).multiply(scaleInternal(b), MC);
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return scaleInternal(a).divide(scaleInternal(b), INTERNAL_SCALE, ROUNDING);
    }

    /**
     * Computes base^exponent. The exponent step uses double only inside this helper,
     * then converts back to BigDecimal at INTERNAL_SCALE.
     */
    public static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
        BigDecimal scaledBase = scaleInternal(base);
        double baseDouble = scaledBase.doubleValue();
        double exponentDouble = exponent.doubleValue();
        double result = Math.pow(baseDouble, exponentDouble);
        return BigDecimal.valueOf(result).setScale(INTERNAL_SCALE, ROUNDING);
    }

    public static BigDecimal pow(BigDecimal base, int exponent) {
        return pow(base, BigDecimal.valueOf(exponent));
    }

    public static int compare(BigDecimal a, BigDecimal b) {
        return scaleInternal(a).compareTo(scaleInternal(b));
    }

    public static boolean isZero(BigDecimal value) {
        return compare(value, BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return compare(value, BigDecimal.ZERO) > 0;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return compare(a, b) >= 0 ? scaleInternal(a) : scaleInternal(b);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return compare(a, b) <= 0 ? scaleInternal(a) : scaleInternal(b);
    }
}
