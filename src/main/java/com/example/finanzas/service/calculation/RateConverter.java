package com.example.finanzas.service.calculation;

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
        BigDecimal rate = BigDecimalMath.scaleInternal(rateValue);

        if ("TEA".equalsIgnoreCase(rateType)) {
            // TEM = (1 + TEA)^(1/12) - 1
            BigDecimal onePlusTea = BigDecimalMath.add(ONE, rate);
            BigDecimal exponent = BigDecimalMath.divide(ONE, TWELVE);
            BigDecimal compound = BigDecimalMath.pow(onePlusTea, exponent);
            return BigDecimalMath.subtract(compound, ONE);
        }

        if ("TNA".equalsIgnoreCase(rateType)) {
            // TEM = TNA / 12
            return BigDecimalMath.divide(rate, TWELVE);
        }

        throw new IllegalArgumentException("Unsupported rate type: " + rateType + ". Use TEA or TNA.");
    }
}
