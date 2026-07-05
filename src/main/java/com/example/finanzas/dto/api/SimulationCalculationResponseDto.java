package com.example.finanzas.dto.api;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationCalculationResponseDto {

    private Long id;
    private String createdAt;
    private SimulationDraftDto input;
    private SimulationResultsDto results;
}
