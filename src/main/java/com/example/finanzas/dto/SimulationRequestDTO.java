package com.example.finanzas.dto;

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
    @DecimalMin(value = "0", message = "downPaymentPercentage must be >= 0")
    private BigDecimal downPaymentPercentage;

    @NotNull
    @DecimalMin(value = "0", message = "balloonPaymentPercentage must be >= 0")
    private BigDecimal balloonPaymentPercentage;

    @NotBlank
    private String rateType;

    @NotNull
    @DecimalMin(value = "0", message = "rateValue must be >= 0")
    private BigDecimal rateValue;

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
