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
    private static final int CALCULATION_SCALE = INTERNAL_SCALE + 10;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int ROOT_ITERATIONS = 50;

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

    public static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
        BigDecimal scaledBase = scaleInternal(base);
        BigDecimal scaledExponent = scaleInternal(exponent);

        try {
            int integerExponent = scaledExponent.stripTrailingZeros().intValueExact();
            return powInteger(scaledBase, integerExponent);
        } catch (ArithmeticException ignored) {
            return powMonthlyFraction(scaledBase, scaledExponent);
        }
    }

    public static BigDecimal pow(BigDecimal base, int exponent) {
        return powInteger(scaleInternal(base), exponent);
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

    private static BigDecimal powMonthlyFraction(BigDecimal base, BigDecimal exponent) {
        BigDecimal numeratorCandidate = exponent.multiply(BigDecimal.valueOf(MONTHS_PER_YEAR), MC);
        BigDecimal roundedNumerator = numeratorCandidate.setScale(0, ROUNDING);
        BigDecimal difference = numeratorCandidate.subtract(roundedNumerator, MC).abs();

        if (difference.compareTo(of("0.00000001")) > 0) {
            throw new IllegalArgumentException("Only integer exponents and monthly fractions are supported");
        }

        int numerator = roundedNumerator.intValueExact();
        return powRational(base, numerator, MONTHS_PER_YEAR);
    }

    private static BigDecimal powRational(BigDecimal base, int numerator, int denominator) {
        if (numerator == 0) {
            return BigDecimal.ONE.setScale(INTERNAL_SCALE, ROUNDING);
        }
        if (compare(base, BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fractional powers require non-negative base");
        }

        boolean negativeExponent = numerator < 0;
        int absNumerator = Math.abs(numerator);
        int gcd = gcd(absNumerator, denominator);
        int reducedNumerator = absNumerator / gcd;
        int reducedDenominator = denominator / gcd;

        BigDecimal result = powInteger(base, reducedNumerator);
        if (reducedDenominator > 1) {
            result = nthRoot(result, reducedDenominator);
        }

        if (negativeExponent) {
            result = BigDecimal.ONE.divide(result, CALCULATION_SCALE, ROUNDING);
        }

        return result.setScale(INTERNAL_SCALE, ROUNDING);
    }

    private static BigDecimal powInteger(BigDecimal base, int exponent) {
        if (exponent == 0) {
            return BigDecimal.ONE.setScale(INTERNAL_SCALE, ROUNDING);
        }

        boolean negativeExponent = exponent < 0;
        int absExponent = Math.abs(exponent);
        BigDecimal result = BigDecimal.ONE.setScale(CALCULATION_SCALE, ROUNDING);
        BigDecimal factor = scaleInternal(base).setScale(CALCULATION_SCALE, ROUNDING);

        while (absExponent > 0) {
            if ((absExponent & 1) == 1) {
                result = result.multiply(factor, MC).setScale(CALCULATION_SCALE, ROUNDING);
            }
            factor = factor.multiply(factor, MC).setScale(CALCULATION_SCALE, ROUNDING);
            absExponent >>= 1;
        }

        if (negativeExponent) {
            result = BigDecimal.ONE.divide(result, CALCULATION_SCALE, ROUNDING);
        }

        return result.setScale(INTERNAL_SCALE, ROUNDING);
    }

    private static BigDecimal nthRoot(BigDecimal value, int root) {
        if (root <= 0) {
            throw new IllegalArgumentException("Root must be positive");
        }
        if (compare(value, BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Root value must be non-negative");
        }
        if (isZero(value) || root == 1) {
            return scaleInternal(value);
        }

        BigDecimal n = BigDecimal.valueOf(root).setScale(CALCULATION_SCALE, ROUNDING);
        BigDecimal nMinusOne = BigDecimal.valueOf(root - 1).setScale(CALCULATION_SCALE, ROUNDING);
        BigDecimal x = BigDecimal.ONE.setScale(CALCULATION_SCALE, ROUNDING);

        for (int i = 0; i < ROOT_ITERATIONS; i++) {
            BigDecimal xToNMinusOne = powIntegerRaw(x, root - 1);
            BigDecimal next = nMinusOne.multiply(x, MC)
                    .add(value.divide(xToNMinusOne, CALCULATION_SCALE, ROUNDING), MC)
                    .divide(n, CALCULATION_SCALE, ROUNDING);
            if (next.subtract(x).abs().compareTo(of("0.00000000001")) <= 0) {
                return next.setScale(INTERNAL_SCALE, ROUNDING);
            }
            x = next;
        }

        return x.setScale(INTERNAL_SCALE, ROUNDING);
    }

    private static BigDecimal powIntegerRaw(BigDecimal base, int exponent) {
        BigDecimal result = BigDecimal.ONE.setScale(CALCULATION_SCALE, ROUNDING);
        BigDecimal factor = base.setScale(CALCULATION_SCALE, ROUNDING);
        int remainingExponent = exponent;

        while (remainingExponent > 0) {
            if ((remainingExponent & 1) == 1) {
                result = result.multiply(factor, MC).setScale(CALCULATION_SCALE, ROUNDING);
            }
            factor = factor.multiply(factor, MC).setScale(CALCULATION_SCALE, ROUNDING);
            remainingExponent >>= 1;
        }

        return result;
    }

    private static int gcd(int a, int b) {
        int x = a;
        int y = b;
        while (y != 0) {
            int remainder = x % y;
            x = y;
            y = remainder;
        }
        return x;
    }
}
