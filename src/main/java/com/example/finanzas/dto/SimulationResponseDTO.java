package com.example.finanzas.dto;

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
public class SimulationResponseDTO {

    private BigDecimal netLoanAmount;
    private BigDecimal downPayment;
    private BigDecimal balloonPayment;
    private BigDecimal monthlyEffectiveRate;
    private BigDecimal regularMonthlyInstallment;
    private BigDecimal monthlyIrr;
    private BigDecimal tcea;
    private BigDecimal npvAtIrr;
    private BigDecimal npvAtReferenceRate;
    private String viability;
    private List<PaymentScheduleRowDTO> schedule;
}
