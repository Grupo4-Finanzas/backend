package com.example.finanzas.service.calculation;

import com.example.finanzas.entity.enums.FrecuenciaCapitalizacion;
import java.math.BigDecimal;

/**
 * Converts input rates to monthly effective rate (TEM).
 */
public final class RateConverter {

    private static final BigDecimal TWELVE = BigDecimalMath.of("12");
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(BigDecimalMath.INTERNAL_SCALE, BigDecimalMath.ROUNDING);

    private RateConverter() {
    }

    /**
     * @param rateType "TEA" (effective annual) or "TNA" (nominal annual)
     * @param rateValue decimal fraction (e.g. 0.12 for 12%)
     * @return monthly effective rate TEM
     */
    public static BigDecimal toMonthlyEffective(String rateType, BigDecimal rateValue) {
        return toMonthlyEffective(rateType, rateValue, FrecuenciaCapitalizacion.Mensual);
    }

    /**
     * @param rateType "TEA" (effective annual) or "TNA" (nominal annual)
     * @param rateValue decimal fraction (e.g. 0.12 for 12%)
     * @param capitalizationFrequency compounding frequency for nominal annual rates
     * @return monthly effective rate TEM
     */
    public static BigDecimal toMonthlyEffective(
            String rateType,
            BigDecimal rateValue,
            FrecuenciaCapitalizacion capitalizationFrequency) {
        BigDecimal rate = BigDecimalMath.scaleInternal(rateValue);

        if ("TEA".equalsIgnoreCase(rateType)) {
            // TEM = (1 + TEA)^(1/12) - 1
            BigDecimal onePlusTea = BigDecimalMath.add(ONE, rate);
            BigDecimal exponent = BigDecimalMath.divide(ONE, TWELVE);
            BigDecimal compound = BigDecimalMath.pow(onePlusTea, exponent);
            return BigDecimalMath.subtract(compound, ONE);
        }

        if ("TNA".equalsIgnoreCase(rateType)) {
            // TEM = (1 + TNA/m)^(m/12) - 1
            BigDecimal compoundingPeriods = BigDecimalMath.of(capitalizationsPerYear(capitalizationFrequency));
            BigDecimal periodicNominalRate = BigDecimalMath.divide(rate, compoundingPeriods);
            BigDecimal exponent = BigDecimalMath.divide(compoundingPeriods, TWELVE);
            BigDecimal compound = BigDecimalMath.pow(BigDecimalMath.add(ONE, periodicNominalRate), exponent);
            return BigDecimalMath.subtract(compound, ONE);
        }

        throw new IllegalArgumentException("Unsupported rate type: " + rateType + ". Use TEA or TNA.");
    }

    private static int capitalizationsPerYear(FrecuenciaCapitalizacion frequency) {
        FrecuenciaCapitalizacion resolved = frequency != null ? frequency : FrecuenciaCapitalizacion.Mensual;
        return switch (resolved) {
            case Diaria -> 360;
            case Quincenal -> 24;
            case Mensual -> 12;
            case Bimestral -> 6;
            case Trimestral -> 4;
            case Cuatrimestral -> 3;
            case Semestral -> 2;
            case Anual -> 1;
        };
    }
}
