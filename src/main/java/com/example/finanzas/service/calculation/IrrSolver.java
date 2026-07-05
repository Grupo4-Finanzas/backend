package com.example.finanzas.service.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Solves monthly IRR via bisection where NPV(cashFlows, rate) = 0.
 */
public final class IrrSolver {

    private static final BigDecimal TOLERANCE = BigDecimalMath.of("0.00001");
    private static final int MAX_ITERATIONS = 100;
    private static final BigDecimal MAX_MONTHLY_RATE = BigDecimalMath.of("0.5");

    private IrrSolver() {
    }

    /**
     * @param cashFlows period 0 = loan received (+), periods 1..n = payments (-)
     * @return monthly IRR as decimal fraction
     */
    public static BigDecimal solveMonthlyIrr(List<BigDecimal> cashFlows) {
        return solveMonthlyIrr(cashFlows, BigDecimalMath.of("0.01"));
    }

    public static BigDecimal solveMonthlyIrr(List<BigDecimal> cashFlows, BigDecimal initialGuess) {
        BigDecimal lower = BigDecimal.ZERO;
        BigDecimal upper = BigDecimalMath.min(
                BigDecimalMath.max(initialGuess, BigDecimalMath.of("0.001")),
                MAX_MONTHLY_RATE);

        BigDecimal npvLower = calculateNpv(cashFlows, lower);
        BigDecimal npvUpper = calculateNpv(cashFlows, upper);

        if (BigDecimalMath.compare(abs(npvLower), TOLERANCE) <= 0) {
            return lower;
        }

        int expansion = 0;
        while (BigDecimalMath.compare(npvLower.multiply(npvUpper), BigDecimal.ZERO) > 0
                && expansion < 15
                && BigDecimalMath.compare(upper, MAX_MONTHLY_RATE) < 0) {
            upper = BigDecimalMath.min(
                    BigDecimalMath.multiply(upper, BigDecimalMath.of("1.5")),
                    MAX_MONTHLY_RATE);
            npvUpper = calculateNpv(cashFlows, upper);
            expansion++;
        }

        if (BigDecimalMath.compare(npvLower.multiply(npvUpper), BigDecimal.ZERO) > 0) {
            return BigDecimalMath.min(initialGuess, MAX_MONTHLY_RATE);
        }

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            BigDecimal mid = BigDecimalMath.divide(
                    BigDecimalMath.add(lower, upper),
                    BigDecimalMath.of("2"));
            BigDecimal npvMid = calculateNpv(cashFlows, mid);

            if (BigDecimalMath.compare(abs(npvMid), TOLERANCE) <= 0) {
                return mid;
            }

            if (BigDecimalMath.compare(npvLower.multiply(npvMid), BigDecimal.ZERO) < 0) {
                upper = mid;
                npvUpper = npvMid;
            } else {
                lower = mid;
                npvLower = npvMid;
            }
        }

        return BigDecimalMath.divide(
                BigDecimalMath.add(lower, upper),
                BigDecimalMath.of("2"));
    }

    /**
     * NPV = sum( CF_t / (1 + r)^t ) for t = 0..n
     */
    public static BigDecimal calculateNpv(List<BigDecimal> cashFlows, BigDecimal rate) {
        BigDecimal npv = BigDecimalMath.zero();
        BigDecimal onePlusRate = BigDecimalMath.add(BigDecimal.ONE, rate);

        for (int t = 0; t < cashFlows.size(); t++) {
            BigDecimal discountFactor = BigDecimalMath.pow(onePlusRate, t);
            BigDecimal discounted = BigDecimalMath.divide(cashFlows.get(t), discountFactor);
            npv = BigDecimalMath.add(npv, discounted);
        }

        return npv;
    }

    private static BigDecimal abs(BigDecimal value) {
        return BigDecimalMath.compare(value, BigDecimal.ZERO) < 0
                ? BigDecimalMath.multiply(value, BigDecimalMath.of("-1"))
                : value;
    }
}
