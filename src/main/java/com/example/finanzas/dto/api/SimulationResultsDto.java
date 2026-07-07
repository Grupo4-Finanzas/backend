package com.example.finanzas.dto.api;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResultsDto {

    private String currency;
    private BigDecimal monthlyPayment;
    private String includedCostsDescription;

    private BigDecimal initialCapital;
    private Integer termMonths;
    private BigDecimal effectiveRatePercentage;
    private BigDecimal temPercentage;

    private BigDecimal tceaPercentage;
    private BigDecimal van;
    private BigDecimal tirPercentage;
    private String viability;

    private List<InterestAmortizationChartItemDto> interestAmortizationChart;
    private List<BalanceEvolutionPointDto> balanceEvolution;
    private List<PaymentScheduleRowApiDto> schedule;
}
