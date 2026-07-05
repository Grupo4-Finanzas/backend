package com.example.finanzas.dto.api;

import jakarta.validation.constraints.DecimalMin;
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
    @DecimalMin(value = "0", message = "lifeInsuranceMonthlyRatePercentage must be >= 0")
    private BigDecimal lifeInsuranceMonthlyRatePercentage;

    @NotNull
    @DecimalMin(value = "0", message = "administrativeExpenses must be >= 0")
    private BigDecimal administrativeExpenses;

    @NotNull
    @DecimalMin(value = "0", message = "vehicleInsuranceAnnualRatePercentage must be >= 0")
    private BigDecimal vehicleInsuranceAnnualRatePercentage;
}
