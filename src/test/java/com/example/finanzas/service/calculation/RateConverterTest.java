package com.example.finanzas.service.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RateConverterTest {

    @Test
    void tea_convertsToMonthlyEffective() {
        BigDecimal tem = RateConverter.toMonthlyEffective("TEA", BigDecimalMath.of("0.12"));
        assertTrue(tem.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(tem.compareTo(BigDecimalMath.of("0.12")) < 0);
    }

    @Test
    void tna_dividesByTwelve() {
        BigDecimal tem = RateConverter.toMonthlyEffective("TNA", BigDecimalMath.of("0.12"));
        assertEquals(0, tem.compareTo(BigDecimalMath.of("0.01")));
    }
}
