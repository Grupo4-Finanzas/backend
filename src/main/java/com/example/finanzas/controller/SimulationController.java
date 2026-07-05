package com.example.finanzas.controller;

import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.dto.api.PageResponseDto;
import com.example.finanzas.dto.api.PaymentScheduleRowApiDto;
import com.example.finanzas.dto.api.SimulationCalculationResponseDto;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.dto.api.SimulationHistoryItemDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.service.SimulationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /** Engine-only endpoint for direct calculation testing (no auth, no persistence). */
    @PostMapping("/calculate-engine")
    public SimulationResponseDTO calculateEngine(@Valid @RequestBody SimulationRequestDTO request) {
        return simulationService.calculateEngineOnly(request);
    }

    @PostMapping("/calculate")
    public SimulationCalculationResponseDto calculate(
            @Valid @RequestBody SimulationDraftDto draft,
            @AuthenticationPrincipal Cliente cliente) {
        return simulationService.calculateAndPersist(draft, cliente);
    }

    @GetMapping("/{id}")
    public SimulationCalculationResponseDto getById(
            @PathVariable Long id,
            @AuthenticationPrincipal Cliente cliente) {
        return simulationService.getById(id, cliente);
    }

    @GetMapping("/history")
    public List<SimulationHistoryItemDto> getHistory(@AuthenticationPrincipal Cliente cliente) {
        return simulationService.getHistory(cliente);
    }

    @GetMapping("/history/page")
    public PageResponseDto<SimulationHistoryItemDto> getHistoryPage(
            @AuthenticationPrincipal Cliente cliente,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return simulationService.getHistoryPage(cliente, page, size, createdFrom, createdTo);
    }

    @GetMapping("/{id}/schedule")
    public List<PaymentScheduleRowApiDto> getSchedule(
            @PathVariable Long id,
            @AuthenticationPrincipal Cliente cliente) {
        return simulationService.getSchedule(id, cliente);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Cliente cliente) {
        simulationService.delete(id, cliente);
    }
}
