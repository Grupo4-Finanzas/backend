package com.example.finanzas.service;

import com.example.finanzas.dto.api.DashboardDataDto;
import com.example.finanzas.dto.api.RecommendedVehicleDto;
import com.example.finanzas.dto.api.SimulationHistoryItemDto;
import com.example.finanzas.dto.api.SimulationSummaryDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.entity.Credito;
import com.example.finanzas.repository.CreditoRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CreditoRepository creditoRepository;
    private final SimulationService simulationService;

    public DashboardDataDto getDashboard(Cliente cliente) {
        List<Credito> creditos = creditoRepository
                .findByClienteIdClienteOrderByFechaCreacionDesc(cliente.getIdCliente());

        List<SimulationHistoryItemDto> history = simulationService.getHistory(cliente).stream()
                .limit(5)
                .toList();

        SimulationSummaryDto summary = buildSummary(creditos, history);

        RecommendedVehicleDto recommendedVehicle = RecommendedVehicleDto.builder()
                .name("Model S Pro")
                .price(new BigDecimal("28400.00"))
                .description("Vehículo recomendado para simulación de crédito vehicular.")
                .imageUrl("https://lh3.googleusercontent.com/aida-public/example")
                .build();

        return DashboardDataDto.builder()
                .summary(summary)
                .simulations(history)
                .recommendedVehicle(recommendedVehicle)
                .build();
    }

    private SimulationSummaryDto buildSummary(List<Credito> creditos, List<SimulationHistoryItemDto> history) {
        if (creditos.isEmpty()) {
            return SimulationSummaryDto.builder()
                    .tcea(BigDecimal.ZERO)
                    .van(BigDecimal.ZERO)
                    .monthlyPayment(BigDecimal.ZERO)
                    .termMonths(0)
                    .build();
        }

        Credito latest = creditos.getFirst();
        BigDecimal averageMonthlyPayment = history.isEmpty()
                ? latest.getCuotaMensualOrdinaria()
                : history.getFirst().getMonthlyPayment();
        return SimulationSummaryDto.builder()
                .tcea(latest.getTcea())
                .van(latest.getVan())
                .monthlyPayment(averageMonthlyPayment)
                .termMonths(latest.getPlazoMeses())
                .build();
    }
}
