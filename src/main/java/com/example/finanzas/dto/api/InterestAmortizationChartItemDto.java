package com.example.finanzas.dto.api;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestAmortizationChartItemDto {

    private int period;
    private BigDecimal interestPercentage;
    private BigDecimal capitalPercentage;
}
