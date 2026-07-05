package com.example.finanzas.dto.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostsConfigurationDto {

    @NotNull
    private BigDecimal lifeInsuranceMonthlyRatePercentage;

    @NotNull
    private BigDecimal administrativeExpenses;

    @NotNull
    private BigDecimal vehicleInsuranceAnnualRatePercentage;
}
