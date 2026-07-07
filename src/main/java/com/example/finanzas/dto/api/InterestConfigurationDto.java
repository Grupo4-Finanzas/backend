package com.example.finanzas.dto.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestConfigurationDto {

    @NotBlank
    @Pattern(regexp = "TEA|TNA|Efectiva|Nominal", message = "rateType must be TEA, TNA, Efectiva or Nominal")
    private String rateType;

    @NotNull
    @DecimalMin(value = "0.0000001", message = "rateValuePercentage must be greater than 0")
    private BigDecimal rateValuePercentage;

    @NotBlank
    @Pattern(regexp = "MONTHLY", message = "paymentFrequency must be MONTHLY")
    private String paymentFrequency;

    private String capitalizationFrequency;
}
