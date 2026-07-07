package com.example.finanzas.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDataDto {

    @NotBlank
    @Pattern(regexp = "PEN|USD", message = "currency must be PEN or USD")
    private String currency;

    @NotNull
    @Positive
    private BigDecimal vehiclePrice;
}
