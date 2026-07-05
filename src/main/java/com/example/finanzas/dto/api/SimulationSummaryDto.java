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
public class SimulationSummaryDto {

    private BigDecimal tcea;
    private BigDecimal van;
    private BigDecimal monthlyPayment;
    private Integer termMonths;
}
