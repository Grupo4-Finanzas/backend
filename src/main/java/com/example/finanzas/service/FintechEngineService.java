package com.example.finanzas.service;

import com.example.finanzas.dto.PaymentScheduleRowDTO;
import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.service.calculation.BigDecimalMath;
import com.example.finanzas.service.calculation.IrrSolver;
import com.example.finanzas.service.calculation.RateConverter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Financial simulation engine for Compra Inteligente vehicle credit.
 * All calculations use BigDecimal exclusively with internal scale 10.
 */
@Service
public class FintechEngineService {

    private static final BigDecimal TWELVE = BigDecimalMath.of("12");

    public SimulationResponseDTO calculate(SimulationRequestDTO request) {
        validateRequest(request);

        BigDecimal vehiclePrice = BigDecimalMath.scaleInternal(request.getVehiclePrice());

        // Step 1: Initial financial setup
        // CI = vehiclePrice × downPaymentPercentage
        BigDecimal downPayment = BigDecimalMath.multiply(vehiclePrice, request.getDownPaymentPercentage());
        // P = vehiclePrice - CI
        BigDecimal netLoanAmount = BigDecimalMath.subtract(vehiclePrice, downPayment);
        // CB = vehiclePrice × balloonPaymentPercentage
        BigDecimal balloonPayment = BigDecimalMath.multiply(vehiclePrice, request.getBalloonPaymentPercentage());

        BigDecimal tem = RateConverter.toMonthlyEffective(request.getRateType(), request.getRateValue());

        BigDecimal currentBalance = netLoanAmount;
        List<PaymentScheduleRowDTO> schedule = new ArrayList<>();
        List<BigDecimal> cashFlows = new ArrayList<>();
        cashFlows.add(netLoanAmount); // CF[0] = +P (deudor receives loan)

        String graceType = request.getGracePeriodType().toUpperCase();
        int graceMonths = request.getGracePeriodMonths();

        // Step 2: Grace period processing
        if ("TOTAL".equals(graceType)) {
            currentBalance = processTotalGrace(
                    request, vehiclePrice, tem, currentBalance, graceMonths, schedule, cashFlows);
        } else if ("PARTIAL".equals(graceType)) {
            processPartialGrace(
                    request, vehiclePrice, tem, currentBalance, graceMonths, schedule, cashFlows);
            // currentBalance unchanged for partial grace
        } else if (graceMonths > 0) {
            throw new IllegalArgumentException("gracePeriodType must be TOTAL or PARTIAL when gracePeriodMonths > 0");
        }

        // Step 3: French cuota with balloon adjustment
        int remainingTermMonths = request.getTotalTermMonths() - graceMonths;
        BigDecimal regularMonthlyInstallment = calculateFrenchInstallmentWithBalloon(
                currentBalance, balloonPayment, tem, remainingTermMonths);

        // Step 4: Standard payment schedule generation
        for (int month = graceMonths + 1; month <= request.getTotalTermMonths(); month++) {
            BigDecimal initialBalanceOfMonth = currentBalance;

            BigDecimal interest = BigDecimalMath.multiply(initialBalanceOfMonth, tem);
            BigDecimal desgravamenInsurance = BigDecimalMath.multiply(
                    initialBalanceOfMonth, request.getMonthlyDesgravamenRate());
            BigDecimal vehicleInsurance = BigDecimalMath.multiply(
                    vehiclePrice, request.getMonthlyVehicleInsuranceRate());
            BigDecimal amortization = BigDecimalMath.subtract(regularMonthlyInstallment, interest);

            BigDecimal balloonPaymentPaid = month == request.getTotalTermMonths()
                    ? balloonPayment
                    : BigDecimalMath.zero();

            BigDecimal totalMonthlyPayment = BigDecimalMath.add(
                    BigDecimalMath.add(regularMonthlyInstallment, desgravamenInsurance),
                    BigDecimalMath.add(vehicleInsurance,
                            BigDecimalMath.add(request.getMonthlyAdministrativeExpense(), balloonPaymentPaid)));

            BigDecimal finalBalanceOfMonth = BigDecimalMath.subtract(initialBalanceOfMonth, amortization);
            if (BigDecimalMath.compare(finalBalanceOfMonth, BigDecimal.ZERO) < 0) {
                finalBalanceOfMonth = BigDecimalMath.zero();
            }

            schedule.add(buildScheduleRow(
                    month, initialBalanceOfMonth, interest, amortization,
                    desgravamenInsurance, vehicleInsurance, request.getMonthlyAdministrativeExpense(),
                    balloonPaymentPaid, regularMonthlyInstallment, totalMonthlyPayment, finalBalanceOfMonth));

            cashFlows.add(BigDecimalMath.multiply(totalMonthlyPayment, BigDecimalMath.of("-1")));
            currentBalance = finalBalanceOfMonth;
        }

        // Step 5: Dynamic metrics — IRR, TCEA, NPV
        BigDecimal monthlyIrr = IrrSolver.solveMonthlyIrr(cashFlows, tem);
        BigDecimal npvAtIrr = IrrSolver.calculateNpv(cashFlows, monthlyIrr);

        // TCEA = (1 + monthlyIRR)^12 - 1
        BigDecimal onePlusIrr = BigDecimalMath.add(BigDecimal.ONE, monthlyIrr);
        BigDecimal tcea = BigDecimalMath.subtract(BigDecimalMath.pow(onePlusIrr, TWELVE), BigDecimal.ONE);

        BigDecimal npvAtReferenceRate = null;
        String viability = "NOT_VIABLE";
        if (request.getReferenceDiscountRate() != null) {
            BigDecimal monthlyReferenceRate = BigDecimalMath.divide(
                    request.getReferenceDiscountRate(), TWELVE);
            npvAtReferenceRate = IrrSolver.calculateNpv(cashFlows, monthlyReferenceRate);
            viability = BigDecimalMath.compare(npvAtReferenceRate, BigDecimal.ZERO) >= 0
                    ? "VIABLE" : "NOT_VIABLE";
        }

        return SimulationResponseDTO.builder()
                .netLoanAmount(roundOutput(netLoanAmount))
                .downPayment(roundOutput(downPayment))
                .balloonPayment(roundOutput(balloonPayment))
                .monthlyEffectiveRate(roundOutput(tem))
                .regularMonthlyInstallment(roundOutput(regularMonthlyInstallment))
                .monthlyIrr(roundOutput(monthlyIrr))
                .tcea(roundOutput(tcea))
                .npvAtIrr(roundOutput(npvAtIrr))
                .npvAtReferenceRate(npvAtReferenceRate != null ? roundOutput(npvAtReferenceRate) : null)
                .viability(viability)
                .schedule(schedule)
                .build();
    }

    /**
     * French amortization with balloon:
     * C = (P' - CB/(1+r)^n) × r / (1 - (1+r)^(-n))
     */
    BigDecimal calculateFrenchInstallmentWithBalloon(
            BigDecimal currentBalance,
            BigDecimal balloonPayment,
            BigDecimal tem,
            int remainingTermMonths) {

        if (remainingTermMonths <= 0) {
            return BigDecimalMath.zero();
        }

        if (BigDecimalMath.isZero(tem)) {
            BigDecimal balloonPv = BigDecimalMath.divide(
                    balloonPayment,
                    BigDecimalMath.pow(BigDecimalMath.add(BigDecimal.ONE, tem), remainingTermMonths));
            return BigDecimalMath.divide(
                    BigDecimalMath.subtract(currentBalance, balloonPv),
                    BigDecimalMath.of(remainingTermMonths));
        }

        BigDecimal onePlusTem = BigDecimalMath.add(BigDecimal.ONE, tem);
        BigDecimal pvBalloon = BigDecimalMath.divide(
                balloonPayment,
                BigDecimalMath.pow(onePlusTem, remainingTermMonths));

        BigDecimal numeratorFactor = BigDecimalMath.subtract(currentBalance, pvBalloon);
        BigDecimal denominatorFactor = BigDecimalMath.divide(
                BigDecimalMath.subtract(BigDecimal.ONE, BigDecimalMath.pow(onePlusTem, -remainingTermMonths)),
                tem);

        return BigDecimalMath.multiply(numeratorFactor, denominatorFactor);
    }

    private BigDecimal processTotalGrace(
            SimulationRequestDTO request,
            BigDecimal vehiclePrice,
            BigDecimal tem,
            BigDecimal currentBalance,
            int graceMonths,
            List<PaymentScheduleRowDTO> schedule,
            List<BigDecimal> cashFlows) {

        for (int month = 1; month <= graceMonths; month++) {
            BigDecimal initialBalance = currentBalance;
            BigDecimal interest = BigDecimalMath.multiply(currentBalance, tem);
            BigDecimal desgravamen = BigDecimalMath.multiply(currentBalance, request.getMonthlyDesgravamenRate());
            BigDecimal vehicleInsurance = BigDecimalMath.multiply(vehiclePrice, request.getMonthlyVehicleInsuranceRate());
            BigDecimal totalMonthlyPayment = BigDecimalMath.add(
                    BigDecimalMath.add(desgravamen, vehicleInsurance),
                    request.getMonthlyAdministrativeExpense());

            schedule.add(buildScheduleRow(
                    month, initialBalance, interest, BigDecimalMath.zero(),
                    desgravamen, vehicleInsurance, request.getMonthlyAdministrativeExpense(),
                    BigDecimalMath.zero(), BigDecimalMath.zero(), totalMonthlyPayment,
                    BigDecimalMath.add(currentBalance, interest)));

            cashFlows.add(BigDecimalMath.multiply(totalMonthlyPayment, BigDecimalMath.of("-1")));
            currentBalance = BigDecimalMath.add(currentBalance, interest);
        }
        return currentBalance;
    }

    private void processPartialGrace(
            SimulationRequestDTO request,
            BigDecimal vehiclePrice,
            BigDecimal tem,
            BigDecimal currentBalance,
            int graceMonths,
            List<PaymentScheduleRowDTO> schedule,
            List<BigDecimal> cashFlows) {

        for (int month = 1; month <= graceMonths; month++) {
            BigDecimal initialBalance = currentBalance;
            BigDecimal interest = BigDecimalMath.multiply(currentBalance, tem);
            BigDecimal desgravamen = BigDecimalMath.multiply(currentBalance, request.getMonthlyDesgravamenRate());
            BigDecimal vehicleInsurance = BigDecimalMath.multiply(vehiclePrice, request.getMonthlyVehicleInsuranceRate());
            BigDecimal totalMonthlyPayment = BigDecimalMath.add(
                    BigDecimalMath.add(interest, desgravamen),
                    BigDecimalMath.add(vehicleInsurance, request.getMonthlyAdministrativeExpense()));

            schedule.add(buildScheduleRow(
                    month, initialBalance, interest, BigDecimalMath.zero(),
                    desgravamen, vehicleInsurance, request.getMonthlyAdministrativeExpense(),
                    BigDecimalMath.zero(), interest, totalMonthlyPayment, currentBalance));

            cashFlows.add(BigDecimalMath.multiply(totalMonthlyPayment, BigDecimalMath.of("-1")));
        }
    }

    private PaymentScheduleRowDTO buildScheduleRow(
            int period,
            BigDecimal initialBalance,
            BigDecimal interest,
            BigDecimal amortization,
            BigDecimal desgravamen,
            BigDecimal vehicleInsurance,
            BigDecimal adminExpense,
            BigDecimal balloonPayment,
            BigDecimal regularInstallment,
            BigDecimal totalPayment,
            BigDecimal finalBalance) {

        return PaymentScheduleRowDTO.builder()
                .period(period)
                .initialBalance(roundOutput(initialBalance))
                .interest(roundOutput(interest))
                .amortization(roundOutput(amortization))
                .desgravamenInsurance(roundOutput(desgravamen))
                .vehicleInsurance(roundOutput(vehicleInsurance))
                .administrativeExpense(roundOutput(adminExpense))
                .balloonPayment(roundOutput(balloonPayment))
                .regularInstallment(roundOutput(regularInstallment))
                .totalMonthlyPayment(roundOutput(totalPayment))
                .finalBalance(roundOutput(finalBalance))
                .build();
    }

    private BigDecimal roundOutput(BigDecimal value) {
        return BigDecimalMath.scaleOutput(value);
    }

    private void validateRequest(SimulationRequestDTO request) {
        if (request.getTotalTermMonths() <= request.getGracePeriodMonths()) {
            throw new IllegalArgumentException("totalTermMonths must be greater than gracePeriodMonths");
        }
        if ("NONE".equalsIgnoreCase(request.getGracePeriodType()) && request.getGracePeriodMonths() != 0) {
            throw new IllegalArgumentException("gracePeriodMonths must be 0 when gracePeriodType is NONE");
        }
    }
}
