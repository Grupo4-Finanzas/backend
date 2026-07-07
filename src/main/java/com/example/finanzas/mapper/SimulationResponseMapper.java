package com.example.finanzas.mapper;

import com.example.finanzas.dto.PaymentScheduleRowDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.dto.api.BalanceEvolutionPointDto;
import com.example.finanzas.dto.api.InterestAmortizationChartItemDto;
import com.example.finanzas.dto.api.PaymentScheduleRowApiDto;
import com.example.finanzas.dto.api.SimulationCalculationResponseDto;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.dto.api.SimulationHistoryItemDto;
import com.example.finanzas.dto.api.SimulationResultsDto;
import com.example.finanzas.entity.Credito;
import com.example.finanzas.entity.enums.EstadoSimulacion;
import com.example.finanzas.service.calculation.BigDecimalMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SimulationResponseMapper {

    private static final BigDecimal HUNDRED = BigDecimalMath.of("100");

    public SimulationCalculationResponseDto toApiResponse(
            Long id,
            Instant createdAt,
            SimulationDraftDto input,
            SimulationResponseDTO engineResponse) {

        BigDecimal tceaPct = decimalToPercent(engineResponse.getTcea());
        BigDecimal tirPct = decimalToPercent(engineResponse.getMonthlyIrr());

        List<PaymentScheduleRowApiDto> schedule = engineResponse.getSchedule().stream()
                .map(this::toApiScheduleRow)
                .toList();

        SimulationResultsDto results = SimulationResultsDto.builder()
                .currency(input.getVehicle().getCurrency())
                .monthlyPayment(engineResponse.getRegularMonthlyInstallment())
                .includedCostsDescription(
                        "Esta cuota incluye seguro de desgravamen, seguro vehicular y gastos administrativos.")
                .initialCapital(engineResponse.getNetLoanAmount())
                .termMonths(input.getCredit().getTermMonths())
                .effectiveRatePercentage(input.getInterest().getRateValuePercentage())
                .tceaPercentage(tceaPct)
                .van(engineResponse.getNpvAtReferenceRate())
                .tirPercentage(tirPct)
                .viability(engineResponse.getViability())
                .interestAmortizationChart(buildChart(engineResponse.getSchedule()))
                .balanceEvolution(buildBalanceEvolution(engineResponse.getSchedule()))
                .schedule(schedule)
                .build();

        return SimulationCalculationResponseDto.builder()
                .id(id)
                .createdAt(createdAt.toString())
                .input(input)
                .results(results)
                .build();
    }

    public SimulationHistoryItemDto toHistoryItem(
            Credito credito,
            SimulationCalculationResponseDto response) {

        return SimulationHistoryItemDto.builder()
                .id(credito.getIdCredito())
                .createdAt(credito.getFechaCreacion().toString())
                .vehiclePrice(credito.getVehiculo().getPrecio())
                .currency(credito.getVehiculo().getMoneda().name())
                .tceaPercentage(response.getResults().getTceaPercentage())
                .monthlyPayment(response.getResults().getMonthlyPayment())
                .termMonths(credito.getPlazoMeses())
                .status(mapEstado(credito.getEstado()))
                .build();
    }

    public SimulationCalculationResponseDto fromCredito(Credito credito, SimulationDraftDto input) {
        SimulationResultsDto results = SimulationResultsDto.builder()
                .currency(credito.getVehiculo().getMoneda().name())
                .monthlyPayment(credito.getCuotaMensualOrdinaria())
                .includedCostsDescription(
                        "Esta cuota incluye seguro de desgravamen, seguro vehicular y gastos administrativos.")
                .initialCapital(credito.getMontoFinanciado())
                .termMonths(credito.getPlazoMeses())
                .effectiveRatePercentage(credito.getValorTasa())
                .tceaPercentage(credito.getTcea())
                .van(credito.getVan())
                .tirPercentage(credito.getTir())
                .viability(credito.getVan() != null && credito.getVan().compareTo(BigDecimal.ZERO) >= 0
                        ? "VIABLE" : "NOT_VIABLE")
                .schedule(List.of())
                .interestAmortizationChart(List.of())
                .balanceEvolution(List.of())
                .build();

        return SimulationCalculationResponseDto.builder()
                .id(credito.getIdCredito())
                .createdAt(credito.getFechaCreacion().toString())
                .input(input)
                .results(results)
                .build();
    }

    private PaymentScheduleRowApiDto toApiScheduleRow(PaymentScheduleRowDTO row) {
        BigDecimal insurance = BigDecimalMath.scaleOutput(
                BigDecimalMath.add(row.getDesgravamenInsurance(), row.getVehicleInsurance()));

        return PaymentScheduleRowApiDto.builder()
                .period(row.getPeriod())
                .paymentDate(row.getPaymentDate() != null ? row.getPaymentDate().toString() : null)
                .initialBalance(row.getInitialBalance())
                .amortization(row.getAmortization())
                .interest(row.getInterest())
                .insurance(insurance)
                .administrativeExpenses(row.getAdministrativeExpense())
                .costs(BigDecimalMath.scaleOutput(
                        BigDecimalMath.add(insurance, row.getAdministrativeExpense())))
                .totalPayment(row.getTotalMonthlyPayment())
                .finalBalance(row.getFinalBalance())
                .status("PENDING")
                .build();
    }

    public PaymentScheduleRowApiDto toApiScheduleRow(com.example.finanzas.entity.Cronograma row) {
        BigDecimal insurance = BigDecimalMath.scaleOutput(
                BigDecimalMath.add(row.getCuotaSeguroDesgravamen(), row.getCuotaSeguroVehicular()));

        return PaymentScheduleRowApiDto.builder()
                .period(row.getPeriodo())
                .paymentDate(row.getFechaPago() != null ? row.getFechaPago().toString() : null)
                .initialBalance(row.getSaldoInicial())
                .amortization(row.getAmortizacion())
                .interest(row.getInteres())
                .insurance(insurance)
                .administrativeExpenses(row.getGastosAdministrativos())
                .costs(BigDecimalMath.scaleOutput(
                        BigDecimalMath.add(insurance, row.getGastosAdministrativos())))
                .totalPayment(row.getCuotaTotal())
                .finalBalance(row.getSaldoFinal())
                .status("PENDING")
                .build();
    }

    private List<InterestAmortizationChartItemDto> buildChart(List<PaymentScheduleRowDTO> schedule) {
        if (schedule.isEmpty()) {
            return List.of();
        }

        List<InterestAmortizationChartItemDto> chart = new ArrayList<>();
        int[] samplePeriods = {1, 6, 12, 24, 36};
        BigDecimal firstInterest = schedule.getFirst().getInterest();

        for (int period : samplePeriods) {
            if (period > schedule.size()) {
                continue;
            }
            PaymentScheduleRowDTO row = schedule.get(period - 1);
            BigDecimal interestPct = BigDecimalMath.isZero(firstInterest)
                    ? BigDecimal.ZERO
                    : BigDecimalMath.scaleOutput(BigDecimalMath.divide(row.getInterest(), firstInterest)
                            .multiply(HUNDRED));
            BigDecimal capitalPct = BigDecimalMath.isZero(row.getInitialBalance())
                    ? BigDecimal.ZERO
                    : BigDecimalMath.scaleOutput(BigDecimalMath.divide(row.getAmortization(), row.getInitialBalance())
                            .multiply(HUNDRED));

            chart.add(InterestAmortizationChartItemDto.builder()
                    .period(period)
                    .interestPercentage(interestPct)
                    .capitalPercentage(capitalPct)
                    .build());
        }

        PaymentScheduleRowDTO last = schedule.getLast();
        chart.add(InterestAmortizationChartItemDto.builder()
                .period(last.getPeriod())
                .interestPercentage(BigDecimalMath.scaleOutput(BigDecimalMath.of("15")))
                .capitalPercentage(BigDecimalMath.scaleOutput(HUNDRED))
                .build());

        return chart;
    }

    private List<BalanceEvolutionPointDto> buildBalanceEvolution(List<PaymentScheduleRowDTO> schedule) {
        if (schedule.isEmpty()) {
            return List.of();
        }

        List<BalanceEvolutionPointDto> points = new ArrayList<>();
        int size = schedule.size();
        int[] sampleIndices = {0, size / 4, size / 2, (size * 3) / 4, size - 1};

        for (int idx : sampleIndices) {
            if (idx < 0 || idx >= size) {
                continue;
            }
            PaymentScheduleRowDTO row = schedule.get(idx);
            points.add(BalanceEvolutionPointDto.builder()
                    .period(row.getPeriod())
                    .balance(row.getInitialBalance())
                    .build());
        }

        points.add(BalanceEvolutionPointDto.builder()
                .period(schedule.getLast().getPeriod())
                .balance(schedule.getLast().getFinalBalance())
                .build());

        return points;
    }

    private BigDecimal decimalToPercent(BigDecimal decimal) {
        if (decimal == null) {
            return null;
        }
        return BigDecimalMath.scaleOutput(decimal.multiply(HUNDRED));
    }

    private String mapEstado(EstadoSimulacion estado) {
        return estado != null ? estado.name() : "CALCULATED";
    }
}
