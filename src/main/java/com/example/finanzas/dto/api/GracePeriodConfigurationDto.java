package com.example.finanzas.dto.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GracePeriodConfigurationDto {

    @NotBlank
    @Pattern(regexp = "NONE|PARTIAL|TOTAL", message = "type must be NONE, PARTIAL or TOTAL")
    private String type;

    @NotNull
    @Min(0)
    @Max(6)
    private Integer months;
}
