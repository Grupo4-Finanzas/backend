package com.example.finanzas.service;

import com.example.finanzas.dto.PaymentScheduleRowDTO;
import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.service.calculation.BigDecimalMath;
import com.example.finanzas.service.calculation.IrrSolver;
import com.example.finanzas.service.calculation.RateConverter;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private static final BigDecimal MIN_DOWN_PAYMENT_PERCENTAGE = BigDecimalMath.of("0.10");
    private static final BigDecimal MAX_DOWN_PAYMENT_PERCENTAGE = BigDecimalMath.of("0.30");
    private static final BigDecimal MIN_BALLOON_PERCENTAGE = BigDecimalMath.of("0.35");
    private static final BigDecimal MAX_BALLOON_PERCENTAGE = BigDecimalMath.of("0.50");

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

        BigDecimal tem = RateConverter.toMonthlyEffective(
                request.getRateType(),
                request.getRateValue(),
                request.getCapitalizationFrequency());

        BigDecimal currentBalance = netLoanAmount;
        List<PaymentScheduleRowDTO> schedule = new ArrayList<>();
        List<BigDecimal> cashFlows = new ArrayList<>();
        cashFlows.add(netLoanAmount); // CF[0] = +P (deudor receives loan)

        String graceType = request.getGracePeriodType().toUpperCase();
        int graceMonths = request.getGracePeriodMonths();

        // Step 2: Grace period processing
        LocalDate firstPaymentDate = LocalDate.now().plusMonths(1);
        if ("TOTAL".equals(graceType)) {
            currentBalance = processTotalGrace(
                    request, vehiclePrice, tem, currentBalance, graceMonths, firstPaymentDate, schedule, cashFlows);
        } else if ("PARTIAL".equals(graceType)) {
            processPartialGrace(
                    request, vehiclePrice, tem, currentBalance, graceMonths, firstPaymentDate, schedule, cashFlows);
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
            boolean isLastMonth = month == request.getTotalTermMonths();

            BigDecimal interest = BigDecimalMath.multiply(initialBalanceOfMonth, tem);
            BigDecimal desgravamenInsurance = BigDecimalMath.multiply(
                    initialBalanceOfMonth, request.getMonthlyDesgravamenRate());
            BigDecimal vehicleInsurance = BigDecimalMath.multiply(
                    vehiclePrice, request.getMonthlyVehicleInsuranceRate());

            BigDecimal balloonPaymentPaid = isLastMonth
                    ? balloonPayment
                    : BigDecimalMath.zero();

            BigDecimal amortization;
            BigDecimal ordinaryPaymentForMonth;
            BigDecimal finalBalanceOfMonth;

            if (isLastMonth) {
                amortization = BigDecimalMath.subtract(initialBalanceOfMonth, balloonPaymentPaid);
                ordinaryPaymentForMonth = BigDecimalMath.add(interest, amortization);
                finalBalanceOfMonth = BigDecimalMath.zero();
            } else {
                amortization = BigDecimalMath.subtract(regularMonthlyInstallment, interest);
                ordinaryPaymentForMonth = regularMonthlyInstallment;
                finalBalanceOfMonth = BigDecimalMath.subtract(initialBalanceOfMonth, amortization);
            }

            BigDecimal totalMonthlyPayment = BigDecimalMath.add(
                    BigDecimalMath.add(ordinaryPaymentForMonth, desgravamenInsurance),
                    BigDecimalMath.add(vehicleInsurance,
                            BigDecimalMath.add(request.getMonthlyAdministrativeExpense(), balloonPaymentPaid)));

            schedule.add(buildScheduleRow(
                    month, firstPaymentDate.plusMonths(month - 1L), initialBalanceOfMonth, interest, amortization,
                    desgravamenInsurance, vehicleInsurance, request.getMonthlyAdministrativeExpense(),
                    balloonPaymentPaid, ordinaryPaymentForMonth, totalMonthlyPayment, finalBalanceOfMonth));

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
            BigDecimal monthlyReferenceRate = RateConverter.toMonthlyEffective(
                    "TEA", request.getReferenceDiscountRate());
            npvAtReferenceRate = IrrSolver.calculateNpv(cashFlows, monthlyReferenceRate);
            viability = BigDecimalMath.compare(npvAtReferenceRate, BigDecimal.ZERO) >= 0
                    ? "VIABLE" : "NOT_VIABLE";
        }

        return SimulationResponseDTO.builder()
                .netLoanAmount(roundOutput(netLoanAmount))
                .downPayment(roundOutput(downPayment))
                .balloonPayment(roundOutput(balloonPayment))
                .monthlyEffectiveRate(BigDecimalMath.scaleRate(tem))
                .regularMonthlyInstallment(roundOutput(regularMonthlyInstallment))
                .monthlyIrr(BigDecimalMath.scaleRate(monthlyIrr))
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

        return BigDecimalMath.divide(numeratorFactor, denominatorFactor);
    }

    private BigDecimal processTotalGrace(
            SimulationRequestDTO request,
            BigDecimal vehiclePrice,
            BigDecimal tem,
            BigDecimal currentBalance,
            int graceMonths,
            LocalDate firstPaymentDate,
            List<PaymentScheduleRowDTO> schedule,
            List<BigDecimal> cashFlows) {

        for (int month = 1; month <= graceMonths; month++) {
            BigDecimal initialBalance = currentBalance;
            BigDecimal interest = BigDecimalMath.multiply(currentBalance, tem);
            BigDecimal finalBalance = BigDecimalMath.add(currentBalance, interest);

            schedule.add(buildScheduleRow(
                    month, firstPaymentDate.plusMonths(month - 1L), initialBalance, interest, BigDecimalMath.zero(),
                    BigDecimalMath.zero(), BigDecimalMath.zero(), BigDecimalMath.zero(),
                    BigDecimalMath.zero(), BigDecimalMath.zero(), BigDecimalMath.zero(),
                    finalBalance));

            cashFlows.add(BigDecimalMath.zero());
            currentBalance = finalBalance;
        }
        return currentBalance;
    }

    private void processPartialGrace(
            SimulationRequestDTO request,
            BigDecimal vehiclePrice,
            BigDecimal tem,
            BigDecimal currentBalance,
            int graceMonths,
            LocalDate firstPaymentDate,
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
                    month, firstPaymentDate.plusMonths(month - 1L), initialBalance, interest, BigDecimalMath.zero(),
                    desgravamen, vehicleInsurance, request.getMonthlyAdministrativeExpense(),
                    BigDecimalMath.zero(), interest, totalMonthlyPayment, currentBalance));

            cashFlows.add(BigDecimalMath.multiply(totalMonthlyPayment, BigDecimalMath.of("-1")));
        }
    }

    private PaymentScheduleRowDTO buildScheduleRow(
            int period,
            LocalDate paymentDate,
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
                .paymentDate(paymentDate)
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
        if (BigDecimalMath.compare(request.getDownPaymentPercentage(), MIN_DOWN_PAYMENT_PERCENTAGE) < 0
                || BigDecimalMath.compare(request.getDownPaymentPercentage(), MAX_DOWN_PAYMENT_PERCENTAGE) > 0) {
            throw new IllegalArgumentException("downPaymentPercentage must be between 0.10 and 0.30");
        }
        if (BigDecimalMath.compare(request.getBalloonPaymentPercentage(), MIN_BALLOON_PERCENTAGE) < 0
                || BigDecimalMath.compare(request.getBalloonPaymentPercentage(), MAX_BALLOON_PERCENTAGE) > 0) {
            throw new IllegalArgumentException("balloonPaymentPercentage must be between 0.35 and 0.50");
        }
        if (request.getTotalTermMonths() != 24
                && request.getTotalTermMonths() != 36
                && request.getTotalTermMonths() != 48) {
            throw new IllegalArgumentException("totalTermMonths must be 24, 36 or 48");
        }
        if (BigDecimalMath.compare(request.getRateValue(), BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("rateValue must be greater than 0");
        }
        if ("TNA".equalsIgnoreCase(request.getRateType()) && request.getCapitalizationFrequency() == null) {
            throw new IllegalArgumentException("capitalizationFrequency is required when rateType is TNA");
        }
        if (request.getTotalTermMonths() <= request.getGracePeriodMonths()) {
            throw new IllegalArgumentException("totalTermMonths must be greater than gracePeriodMonths");
        }
        if ("NONE".equalsIgnoreCase(request.getGracePeriodType()) && request.getGracePeriodMonths() != 0) {
            throw new IllegalArgumentException("gracePeriodMonths must be 0 when gracePeriodType is NONE");
        }
        if (request.getGracePeriodMonths() < 0 || request.getGracePeriodMonths() > 6) {
            throw new IllegalArgumentException("gracePeriodMonths must be between 0 and 6");
        }
    }
}
