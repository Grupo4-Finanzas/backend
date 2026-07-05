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
public class SimulationHistoryItemDto {

    private Long id;
    private String createdAt;
    private BigDecimal vehiclePrice;
    private String currency;
    private BigDecimal tceaPercentage;
    private BigDecimal monthlyPayment;
    private Integer termMonths;
    private String status;
}
