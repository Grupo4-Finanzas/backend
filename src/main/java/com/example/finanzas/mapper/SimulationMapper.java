package com.example.finanzas.mapper;

import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.entity.enums.FrecuenciaCapitalizacion;
import com.example.finanzas.service.calculation.BigDecimalMath;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SimulationMapper {

    private static final BigDecimal HUNDRED = BigDecimalMath.of("100");
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimalMath.of("12");
    private static final BigDecimal BCP_PERIOD_DAYS = BigDecimalMath.of("30");
    private static final BigDecimal BCP_YEAR_DAYS = BigDecimalMath.of("365");

    /**
     * Mapea el borrador del frontend al request del motor financiero.
     *
     * <p>Fórmulas de tasas de seguros aplicadas antes del cálculo:
     * <ul>
     *   <li>Seguro vehicular: {@code tasa periodo = tasa anual * 30/365}</li>
     *   <li>Seguro desgravamen: {@code tasa periodo = tasa mensual * 12 * 30/365}</li>
     * </ul>
     */
    public SimulationRequestDTO toEngineRequest(SimulationDraftDto draft) {
        BigDecimal vehiclePrice = draft.getVehicle().getVehiclePrice();

        String engineRateType = mapRateType(draft.getInterest().getRateType());
        BigDecimal rateValue = percentToDecimal(draft.getInterest().getRateValuePercentage());

        BigDecimal monthlyVehicleInsuranceRate = percentToDecimal(
                draft.getCosts().getVehicleInsuranceAnnualRatePercentage());
        monthlyVehicleInsuranceRate = BigDecimalMath.scaleInternal(BigDecimalMath.multiply(
                monthlyVehicleInsuranceRate,
                BigDecimalMath.divide(BCP_PERIOD_DAYS, BCP_YEAR_DAYS)));

        BigDecimal monthlyDesgravamenRate = percentToDecimal(
                draft.getCosts().getLifeInsuranceMonthlyRatePercentage());
        monthlyDesgravamenRate = BigDecimalMath.scaleInternal(BigDecimalMath.multiply(
                BigDecimalMath.multiply(monthlyDesgravamenRate, MONTHS_PER_YEAR),
                BigDecimalMath.divide(BCP_PERIOD_DAYS, BCP_YEAR_DAYS)));

        return SimulationRequestDTO.builder()
                .vehiclePrice(vehiclePrice)
                .downPaymentPercentage(percentToDecimal(draft.getCredit().getInitialFeePercentage()))
                .balloonPaymentPercentage(percentToDecimal(draft.getCredit().getBalloonFeePercentage()))
                .rateType(engineRateType)
                .rateValue(rateValue)
                .capitalizationFrequency(mapCapitalizationFrequency(draft.getInterest().getCapitalizationFrequency()))
                .totalTermMonths(draft.getCredit().getTermMonths())
                .gracePeriodMonths(draft.getGracePeriod().getMonths())
                .gracePeriodType(draft.getGracePeriod().getType())
                .monthlyDesgravamenRate(monthlyDesgravamenRate)
                .monthlyVehicleInsuranceRate(monthlyVehicleInsuranceRate)
                .monthlyAdministrativeExpense(draft.getCosts().getAdministrativeExpenses())
                .referenceDiscountRate(percentToDecimal(draft.getFinancialAnalysis().getCokAnnualPercentage()))
                .build();
    }

    private String mapRateType(String frontendRateType) {
        if (frontendRateType == null) {
            throw new IllegalArgumentException("Unsupported frontend rate type: null");
        }

        String normalizedRateType = frontendRateType.trim();
        if ("TEA".equalsIgnoreCase(normalizedRateType)
                || "Efectiva".equalsIgnoreCase(normalizedRateType)) {
            return "TEA";
        }
        if ("TNA".equalsIgnoreCase(normalizedRateType)
                || "Nominal".equalsIgnoreCase(normalizedRateType)) {
            return "TNA";
        }
        throw new IllegalArgumentException("Unsupported frontend rate type: " + frontendRateType + ". Use TEA or TNA.");
    }

    private FrecuenciaCapitalizacion mapCapitalizationFrequency(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return FrecuenciaCapitalizacion.Mensual;
        }
        return FrecuenciaCapitalizacion.valueOf(frequency);
    }

    private BigDecimal percentToDecimal(BigDecimal wholePercent) {
        return BigDecimalMath.divide(wholePercent, HUNDRED);
    }
}
