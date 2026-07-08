package com.example.finanzas.dto.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationDraftDto {

    @Valid
    private ClientDataDto client;

    @Valid
    @NotNull
    private VehicleDataDto vehicle;

    @Valid
    @NotNull
    private CreditConfigurationDto credit;

    @Valid
    @NotNull
    private InterestConfigurationDto interest;

    @Valid
    @NotNull
    private GracePeriodConfigurationDto gracePeriod;

    @Valid
    @NotNull
    private FinancialAnalysisConfigurationDto financialAnalysis;

    @Valid
    @NotNull
    private CostsConfigurationDto costs;
}
