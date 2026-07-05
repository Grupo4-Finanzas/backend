package com.example.finanzas.mapper;

import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.service.calculation.BigDecimalMath;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SimulationMapper {

    private static final BigDecimal HUNDRED = BigDecimalMath.of("100");
    private static final BigDecimal TWELVE = BigDecimalMath.of("12");

    public SimulationRequestDTO toEngineRequest(SimulationDraftDto draft) {
        BigDecimal vehiclePrice = draft.getVehicle().getVehiclePrice();

        String engineRateType = mapRateType(draft.getInterest().getRateType());
        BigDecimal rateValue = percentToDecimal(draft.getInterest().getRateValuePercentage());

        BigDecimal monthlyVehicleInsuranceRate = percentToDecimal(
                draft.getCosts().getVehicleInsuranceAnnualRatePercentage());
        monthlyVehicleInsuranceRate = BigDecimalMath.divide(monthlyVehicleInsuranceRate, TWELVE);

        return SimulationRequestDTO.builder()
                .vehiclePrice(vehiclePrice)
                .downPaymentPercentage(percentToDecimal(draft.getCredit().getInitialFeePercentage()))
                .balloonPaymentPercentage(percentToDecimal(draft.getCredit().getBalloonFeePercentage()))
                .rateType(engineRateType)
                .rateValue(rateValue)
                .totalTermMonths(draft.getCredit().getTermMonths())
                .gracePeriodMonths(draft.getGracePeriod().getMonths())
                .gracePeriodType(draft.getGracePeriod().getType())
                .monthlyDesgravamenRate(percentToDecimal(draft.getCosts().getLifeInsuranceMonthlyRatePercentage()))
                .monthlyVehicleInsuranceRate(monthlyVehicleInsuranceRate)
                .monthlyAdministrativeExpense(draft.getCosts().getAdministrativeExpenses())
                .referenceDiscountRate(percentToDecimal(draft.getFinancialAnalysis().getTargetTirPercentage()))
                .build();
    }

    private String mapRateType(String frontendRateType) {
        if ("TEA".equalsIgnoreCase(frontendRateType)) {
            return "TEA";
        }
        if ("TEM".equalsIgnoreCase(frontendRateType)) {
            return "TNA";
        }
        throw new IllegalArgumentException("Unsupported frontend rate type: " + frontendRateType);
    }

    private BigDecimal percentToDecimal(BigDecimal wholePercent) {
        return BigDecimalMath.divide(wholePercent, HUNDRED);
    }
}
