package com.example.finanzas.dto;

import com.example.finanzas.entity.enums.FrecuenciaCapitalizacion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class SimulationRequestDTO {

    @NotNull
    @DecimalMin(value = "0.01", message = "vehiclePrice must be greater than 0")
    private BigDecimal vehiclePrice;

    @NotNull
    @DecimalMin(value = "0.10", message = "downPaymentPercentage must be at least 0.10")
    @DecimalMax(value = "0.30", message = "downPaymentPercentage must be at most 0.30")
    private BigDecimal downPaymentPercentage;

    @NotNull
    @DecimalMin(value = "0.35", message = "balloonPaymentPercentage must be at least 0.35")
    @DecimalMax(value = "0.50", message = "balloonPaymentPercentage must be at most 0.50")
    private BigDecimal balloonPaymentPercentage;

    @NotBlank
    private String rateType;

    @NotNull
    @DecimalMin(value = "0.0000001", message = "rateValue must be greater than 0")
    private BigDecimal rateValue;

    private FrecuenciaCapitalizacion capitalizationFrequency;

    @Min(1)
    private int totalTermMonths;

    @Min(0)
    @Max(6)
    private int gracePeriodMonths;

    @NotBlank
    private String gracePeriodType;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal monthlyDesgravamenRate;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal monthlyVehicleInsuranceRate;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal monthlyAdministrativeExpense;

    /** Optional reference rate (annual decimal) for NPV viability analysis. */
    private BigDecimal referenceDiscountRate;
}
