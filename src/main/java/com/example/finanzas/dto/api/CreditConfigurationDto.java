package com.example.finanzas.dto.api;

import jakarta.validation.constraints.DecimalMax;
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
public class CreditConfigurationDto {

    @NotNull
    @DecimalMin(value = "10", message = "initialFeePercentage must be at least 10")
    @DecimalMax(value = "30", message = "initialFeePercentage must be at most 30")
    private BigDecimal initialFeePercentage;

    @NotNull
    @DecimalMin(value = "35", message = "balloonFeePercentage must be at least 35")
    @DecimalMax(value = "50", message = "balloonFeePercentage must be at most 50")
    private BigDecimal balloonFeePercentage;

    @NotNull
    private Integer termMonths;
}
