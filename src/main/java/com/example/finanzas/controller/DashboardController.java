package com.example.finanzas.controller;

import com.example.finanzas.dto.api.DashboardDataDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardDataDto getDashboard(@AuthenticationPrincipal Cliente cliente) {
        return dashboardService.getDashboard(cliente);
    }
}
