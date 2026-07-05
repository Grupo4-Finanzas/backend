package com.example.finanzas.dto.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDataDto {

    @NotBlank
    @Size(min = 8, max = 8)
    @Pattern(regexp = "\\d{8}", message = "documentNumber must contain exactly 8 digits")
    private String documentNumber;

    @NotBlank
    @Size(max = 100)
    private String fullName;
}
