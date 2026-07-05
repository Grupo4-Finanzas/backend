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
public class PaymentScheduleRowApiDto {

    private int period;
    private BigDecimal initialBalance;
    private BigDecimal amortization;
    private BigDecimal interest;
    private BigDecimal insurance;
    private BigDecimal administrativeExpenses;
    private BigDecimal costs;
    private BigDecimal totalPayment;
    private BigDecimal finalBalance;
    private String status;
}
