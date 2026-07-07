package com.example.finanzas.service.calculation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrrSolverTest {

    @Test
    void solveMonthlyIrr_findsRateThatZeroesNpv() {
        List<BigDecimal> cashFlows = List.of(
                BigDecimalMath.of("10000"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-900"),
                BigDecimalMath.of("-9100"));

        BigDecimal irr = IrrSolver.solveMonthlyIrr(cashFlows);
        BigDecimal npv = IrrSolver.calculateNpv(cashFlows, irr);

        assertTrue(npv.abs().compareTo(BigDecimalMath.of("0.00001")) <= 0);
    }

    @Test
    void solveMonthlyIrr_throwsWhenCashFlowsDoNotBracketRoot() {
        List<BigDecimal> cashFlows = List.of(
                BigDecimalMath.of("10000"),
                BigDecimalMath.of("100"),
                BigDecimalMath.of("100"));

        assertThrows(IllegalArgumentException.class, () -> IrrSolver.solveMonthlyIrr(cashFlows));
    }
}
