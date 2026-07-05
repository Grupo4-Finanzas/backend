package com.example.finanzas.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentScheduleRowDTO {

    private int period;
    private BigDecimal initialBalance;
    private BigDecimal interest;
    private BigDecimal amortization;
    private BigDecimal desgravamenInsurance;
    private BigDecimal vehicleInsurance;
    private BigDecimal administrativeExpense;
    private BigDecimal balloonPayment;
    private BigDecimal regularInstallment;
    private BigDecimal totalMonthlyPayment;
    private BigDecimal finalBalance;
}
