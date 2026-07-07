package com.example.finanzas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.entity.enums.FrecuenciaCapitalizacion;
import com.example.finanzas.mapper.SimulationMapper;
import com.example.finanzas.service.calculation.BigDecimalMath;
import com.example.finanzas.service.calculation.IrrSolver;
import com.example.finanzas.service.calculation.RateConverter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FintechEngineServiceTest {

    private FintechEngineService engine;
    private SimulationMapper simulationMapper;

    @BeforeEach
    void setUp() {
        engine = new FintechEngineService();
        simulationMapper = new SimulationMapper();
    }

    @Test
    void calculate_basicScenario_producesScheduleAndMetrics() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("30000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.35"))
                .rateType("TEA")
                .rateValue(BigDecimalMath.of("0.125"))
                .totalTermMonths(48)
                .gracePeriodMonths(0)
                .gracePeriodType("NONE")
                .monthlyDesgravamenRate(BigDecimalMath.of("0.0005"))
                .monthlyVehicleInsuranceRate(BigDecimalMath.of("0.0029166667"))
                .monthlyAdministrativeExpense(BigDecimalMath.of("10"))
                .referenceDiscountRate(BigDecimalMath.of("0.15"))
                .build();

        SimulationResponseDTO response = engine.calculate(request);

        assertNotNull(response.getRegularMonthlyInstallment());
        assertTrue(response.getRegularMonthlyInstallment().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(48, response.getSchedule().size());
        assertEquals(LocalDate.now().plusMonths(1), response.getSchedule().getFirst().getPaymentDate());
        assertEquals(LocalDate.now().plusMonths(48), response.getSchedule().getLast().getPaymentDate());
        assertNotNull(response.getMonthlyIrr());
        assertNotNull(response.getTcea());
        assertTrue(response.getTcea().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(response.getNpvAtIrr());
    }

    @Test
    void calculate_withBalloonPayment_usesDiscountedBalloonFormula() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("30000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.35"))
                .rateType("TEA")
                .rateValue(BigDecimalMath.of("0.125"))
                .totalTermMonths(48)
                .gracePeriodMonths(0)
                .gracePeriodType("NONE")
                .monthlyDesgravamenRate(BigDecimalMath.zero())
                .monthlyVehicleInsuranceRate(BigDecimalMath.zero())
                .monthlyAdministrativeExpense(BigDecimalMath.zero())
                .build();

        SimulationResponseDTO response = engine.calculate(request);

        assertEquals(0, response.getRegularMonthlyInstallment().compareTo(new BigDecimal("457.99")));
    }

    @Test
    void calculate_withBalloonPayment_discountsBalloonFromFinalBalance() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("30000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.35"))
                .rateType("TEA")
                .rateValue(BigDecimalMath.of("0.125"))
                .totalTermMonths(48)
                .gracePeriodMonths(0)
                .gracePeriodType("NONE")
                .monthlyDesgravamenRate(BigDecimalMath.zero())
                .monthlyVehicleInsuranceRate(BigDecimalMath.zero())
                .monthlyAdministrativeExpense(BigDecimalMath.zero())
                .build();

        SimulationResponseDTO response = engine.calculate(request);

        assertEquals(0, response.getSchedule().getLast().getFinalBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void calculate_npvUsesEffectiveAnnualReferenceRateConvertedToMonthlyEffective() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("30000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.35"))
                .rateType("TEA")
                .rateValue(BigDecimalMath.of("0.125"))
                .totalTermMonths(48)
                .gracePeriodMonths(0)
                .gracePeriodType("NONE")
                .monthlyDesgravamenRate(BigDecimalMath.of("0.0005"))
                .monthlyVehicleInsuranceRate(BigDecimalMath.of("0.0029166667"))
                .monthlyAdministrativeExpense(BigDecimalMath.of("10"))
                .referenceDiscountRate(BigDecimalMath.of("0.15"))
                .build();

        SimulationResponseDTO response = engine.calculate(request);
        List<BigDecimal> cashFlows = new ArrayList<>();
        cashFlows.add(response.getNetLoanAmount());
        response.getSchedule().forEach(row ->
                cashFlows.add(BigDecimalMath.multiply(row.getTotalMonthlyPayment(), BigDecimalMath.of("-1"))));
        BigDecimal monthlyEffectiveReferenceRate = RateConverter.toMonthlyEffective(
                "TEA", BigDecimalMath.of("0.15"));
        BigDecimal expectedNpv = BigDecimalMath.scaleOutput(
                IrrSolver.calculateNpv(cashFlows, monthlyEffectiveReferenceRate));

        BigDecimal difference = response.getNpvAtReferenceRate().subtract(expectedNpv).abs();
        assertTrue(difference.compareTo(new BigDecimal("0.05")) <= 0);
    }

    @Test
    void calculate_totalGrace_increasesBalanceBeforeAmortization() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("25000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.35"))
                .rateType("TNA")
                .rateValue(BigDecimalMath.of("0.12"))
                .capitalizationFrequency(FrecuenciaCapitalizacion.Mensual)
                .totalTermMonths(36)
                .gracePeriodMonths(3)
                .gracePeriodType("TOTAL")
                .monthlyDesgravamenRate(BigDecimalMath.of("0.0005"))
                .monthlyVehicleInsuranceRate(BigDecimalMath.of("0.003"))
                .monthlyAdministrativeExpense(BigDecimalMath.of("10"))
                .build();

        SimulationResponseDTO response = engine.calculate(request);

        assertEquals(36, response.getSchedule().size());
        var firstGraceRow = response.getSchedule().getFirst();
        assertEquals(0, firstGraceRow.getAmortization().compareTo(BigDecimal.ZERO));
        assertEquals(0, firstGraceRow.getDesgravamenInsurance().compareTo(BigDecimal.ZERO));
        assertEquals(0, firstGraceRow.getVehicleInsurance().compareTo(BigDecimal.ZERO));
        assertEquals(0, firstGraceRow.getAdministrativeExpense().compareTo(BigDecimal.ZERO));
        assertEquals(0, firstGraceRow.getTotalMonthlyPayment().compareTo(BigDecimal.ZERO));
        assertTrue(firstGraceRow.getFinalBalance().compareTo(firstGraceRow.getInitialBalance()) > 0);
    }

    @Test
    void calculate_rejectsBalloonPaymentBelowBcpRange() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("30000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.34"))
                .rateType("TEA")
                .rateValue(BigDecimalMath.of("0.125"))
                .totalTermMonths(48)
                .gracePeriodMonths(0)
                .gracePeriodType("NONE")
                .monthlyDesgravamenRate(BigDecimalMath.zero())
                .monthlyVehicleInsuranceRate(BigDecimalMath.zero())
                .monthlyAdministrativeExpense(BigDecimalMath.zero())
                .build();

        assertThrows(IllegalArgumentException.class, () -> engine.calculate(request));
    }

    @Test
    void calculate_rejectsBalloonPaymentAboveBcpRange() {
        SimulationRequestDTO request = SimulationRequestDTO.builder()
                .vehiclePrice(BigDecimalMath.of("30000"))
                .downPaymentPercentage(BigDecimalMath.of("0.20"))
                .balloonPaymentPercentage(BigDecimalMath.of("0.51"))
                .rateType("TEA")
                .rateValue(BigDecimalMath.of("0.125"))
                .totalTermMonths(48)
                .gracePeriodMonths(0)
                .gracePeriodType("NONE")
                .monthlyDesgravamenRate(BigDecimalMath.zero())
                .monthlyVehicleInsuranceRate(BigDecimalMath.zero())
                .monthlyAdministrativeExpense(BigDecimalMath.zero())
                .build();

        assertThrows(IllegalArgumentException.class, () -> engine.calculate(request));
    }

    @Test
    void mapper_convertsFrontendPercentagesToDecimals() {
        SimulationDraftDto draft = SimulationDraftDto.builder()
                .client(com.example.finanzas.dto.api.ClientDataDto.builder()
                        .documentNumber("12345678")
                        .fullName("Test User")
                        .build())
                .vehicle(com.example.finanzas.dto.api.VehicleDataDto.builder()
                        .currency("PEN")
                        .vehiclePrice(new BigDecimal("30000"))
                        .build())
                .credit(com.example.finanzas.dto.api.CreditConfigurationDto.builder()
                        .initialFeePercentage(new BigDecimal("20"))
                        .balloonFeePercentage(new BigDecimal("35"))
                        .termMonths(48)
                        .build())
                .interest(com.example.finanzas.dto.api.InterestConfigurationDto.builder()
                        .rateType("TEA")
                        .rateValuePercentage(new BigDecimal("12.5"))
                        .paymentFrequency("MONTHLY")
                        .build())
                .gracePeriod(com.example.finanzas.dto.api.GracePeriodConfigurationDto.builder()
                        .type("NONE")
                        .months(0)
                        .build())
                .financialAnalysis(com.example.finanzas.dto.api.FinancialAnalysisConfigurationDto.builder()
                        .cokAnnualPercentage(new BigDecimal("15"))
                        .build())
                .costs(com.example.finanzas.dto.api.CostsConfigurationDto.builder()
                        .lifeInsuranceMonthlyRatePercentage(new BigDecimal("0.05"))
                        .administrativeExpenses(new BigDecimal("10"))
                        .vehicleInsuranceAnnualRatePercentage(new BigDecimal("3.5"))
                        .build())
                .build();

        var engineRequest = simulationMapper.toEngineRequest(draft);

        assertEquals(0, engineRequest.getDownPaymentPercentage().compareTo(BigDecimalMath.of("0.20")));
        assertEquals("TEA", engineRequest.getRateType());
        assertEquals(0, engineRequest.getMonthlyDesgravamenRate()
                .compareTo(BigDecimalMath.of("0.0004931507")));
        assertEquals(0, engineRequest.getMonthlyVehicleInsuranceRate()
                .compareTo(BigDecimalMath.of("0.0028767123")));
    }

    @Test
    void mapper_mapsNominalAnnualRateWithCapitalizationFrequency() {
        SimulationDraftDto draft = SimulationDraftDto.builder()
                .client(com.example.finanzas.dto.api.ClientDataDto.builder()
                        .documentNumber("12345678")
                        .fullName("Test User")
                        .build())
                .vehicle(com.example.finanzas.dto.api.VehicleDataDto.builder()
                        .currency("PEN")
                        .vehiclePrice(new BigDecimal("30000"))
                        .build())
                .credit(com.example.finanzas.dto.api.CreditConfigurationDto.builder()
                        .initialFeePercentage(new BigDecimal("20"))
                        .balloonFeePercentage(new BigDecimal("35"))
                        .termMonths(48)
                        .build())
                .interest(com.example.finanzas.dto.api.InterestConfigurationDto.builder()
                        .rateType("TNA")
                        .rateValuePercentage(new BigDecimal("12.5"))
                        .paymentFrequency("MONTHLY")
                        .capitalizationFrequency("Trimestral")
                        .build())
                .gracePeriod(com.example.finanzas.dto.api.GracePeriodConfigurationDto.builder()
                        .type("NONE")
                        .months(0)
                        .build())
                .financialAnalysis(com.example.finanzas.dto.api.FinancialAnalysisConfigurationDto.builder()
                        .cokAnnualPercentage(new BigDecimal("15"))
                        .build())
                .costs(com.example.finanzas.dto.api.CostsConfigurationDto.builder()
                        .lifeInsuranceMonthlyRatePercentage(new BigDecimal("0.05"))
                        .administrativeExpenses(new BigDecimal("10"))
                        .vehicleInsuranceAnnualRatePercentage(new BigDecimal("3.5"))
                        .build())
                .build();

        var engineRequest = simulationMapper.toEngineRequest(draft);

        assertEquals("TNA", engineRequest.getRateType());
        assertEquals(FrecuenciaCapitalizacion.Trimestral, engineRequest.getCapitalizationFrequency());
    }

    @Test
    void mapper_rejectsTemAsNominalAnnualRate() {
        SimulationDraftDto draft = SimulationDraftDto.builder()
                .client(com.example.finanzas.dto.api.ClientDataDto.builder()
                        .documentNumber("12345678")
                        .fullName("Test User")
                        .build())
                .vehicle(com.example.finanzas.dto.api.VehicleDataDto.builder()
                        .currency("PEN")
                        .vehiclePrice(new BigDecimal("30000"))
                        .build())
                .credit(com.example.finanzas.dto.api.CreditConfigurationDto.builder()
                        .initialFeePercentage(new BigDecimal("20"))
                        .balloonFeePercentage(new BigDecimal("35"))
                        .termMonths(48)
                        .build())
                .interest(com.example.finanzas.dto.api.InterestConfigurationDto.builder()
                        .rateType("TEM")
                        .rateValuePercentage(new BigDecimal("12.5"))
                        .paymentFrequency("MONTHLY")
                        .build())
                .gracePeriod(com.example.finanzas.dto.api.GracePeriodConfigurationDto.builder()
                        .type("NONE")
                        .months(0)
                        .build())
                .financialAnalysis(com.example.finanzas.dto.api.FinancialAnalysisConfigurationDto.builder()
                        .cokAnnualPercentage(new BigDecimal("15"))
                        .build())
                .costs(com.example.finanzas.dto.api.CostsConfigurationDto.builder()
                        .lifeInsuranceMonthlyRatePercentage(new BigDecimal("0.05"))
                        .administrativeExpenses(new BigDecimal("10"))
                        .vehicleInsuranceAnnualRatePercentage(new BigDecimal("3.5"))
                        .build())
                .build();

        assertThrows(IllegalArgumentException.class, () -> simulationMapper.toEngineRequest(draft));
    }
}
