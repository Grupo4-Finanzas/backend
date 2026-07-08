package com.example.finanzas.service.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resuelve la TIR mensual y el VAN a partir de los flujos de caja del deudor.
 */
public final class IrrSolver {

    private static final BigDecimal TOLERANCE = BigDecimalMath.of("0.00001");
    private static final int MAX_ITERATIONS = 100;
    private static final BigDecimal MAX_MONTHLY_RATE = BigDecimalMath.of("0.5");

    private IrrSolver() {
    }

    /**
     * Calcula la Tasa Interna de Retorno (TIR) mensual del crédito.
     *
     * <p>Fórmula del profesor (punto de vista del deudor):
     * {@code 0 = F0 + Σ[t=1..N] Ft / (1 + TIR)^t}
     *
     * <p>Donde F0 es el monto financiado recibido, Ft son los pagos mensuales con signo negativo
     * y TIR es la tasa que iguala el valor presente de los flujos a cero.
     * Se resuelve por bisección entre 0 y 0.5 mensual.
     *
     * @param cashFlows periodo 0 = préstamo recibido (+), periodos 1..n = pagos (-)
     * @return TIR mensual como fracción decimal
     */
    public static BigDecimal solveMonthlyIrr(List<BigDecimal> cashFlows) {
        return solveMonthlyIrr(cashFlows, BigDecimalMath.of("0.01"));
    }

    /**
     * Calcula la TIR mensual usando una tasa inicial como punto de partida para la bisección.
     *
     * <p>Fórmula del profesor:
     * {@code 0 = F0 + Σ[t=1..N] Ft / (1 + TIR)^t}
     *
     * @param cashFlows periodo 0 = préstamo recibido (+), periodos 1..n = pagos (-)
     * @param initialGuess estimación inicial de la TIR mensual
     * @return TIR mensual como fracción decimal
     */
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
            throw new IllegalArgumentException("IRR could not be solved because cash flows do not bracket a root");
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
     * Calcula el Valor Actual Neto (VAN) descontando los flujos de caja del deudor.
     *
     * <p>Fórmula del profesor (punto de vista del deudor):
     * {@code VAN = F0 + Σ[t=1..N] Ft / (1 + COK)^t}
     *
     * <p>Donde:
     * <ul>
     *   <li>F0: flujo inicial positivo (monto financiado recibido por el deudor)</li>
     *   <li>Ft: flujo del periodo t (pagos mensuales con signo negativo)</li>
     *   <li>COK: tasa de descuento mensual (COK del deudor o TIR según el caso)</li>
     * </ul>
     *
     * @param cashFlows flujos de caja del deudor (F0 positivo, Ft negativos)
     * @param rate tasa de descuento mensual en fracción decimal
     * @return VAN calculado
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
