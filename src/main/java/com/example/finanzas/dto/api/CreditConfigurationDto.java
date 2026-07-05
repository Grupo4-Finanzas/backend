package com.example.finanzas.dto.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Min(0)
    @Max(100)
    private BigDecimal initialFeePercentage;

    @NotNull
    @Min(0)
    @Max(100)
    private BigDecimal balloonFeePercentage;

    @NotNull
    @Min(1)
    private Integer termMonths;
}
