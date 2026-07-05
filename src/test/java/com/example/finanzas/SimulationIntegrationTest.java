package com.example.finanzas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void calculateEngine_returnsSimulationResults() throws Exception {
        Map<String, Object> request = buildEngineRequest();

        mockMvc.perform(post("/api/v1/simulations/calculate-engine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularMonthlyInstallment").exists())
                .andExpect(jsonPath("$.tcea").exists())
                .andExpect(jsonPath("$.schedule.length()").value(48));
    }

    @Test
    void authRegisterAndCalculateSimulation() throws Exception {
        Map<String, String> register = Map.of(
                "fullName", "Carlos Mendoza",
                "email", "integration@test.com",
                "password", "password123",
                "confirmPassword", "password123");

        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(registerResponse).get("token").asText();

        mockMvc.perform(post("/api/v1/simulations/calculate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFrontendDraft())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.results.tceaPercentage").exists())
                .andExpect(jsonPath("$.results.schedule.length()").value(48));
    }

    private Map<String, Object> buildEngineRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("vehiclePrice", 30000);
        request.put("downPaymentPercentage", 0.20);
        request.put("balloonPaymentPercentage", 0.35);
        request.put("rateType", "TEA");
        request.put("rateValue", 0.125);
        request.put("totalTermMonths", 48);
        request.put("gracePeriodMonths", 0);
        request.put("gracePeriodType", "NONE");
        request.put("monthlyDesgravamenRate", 0.0005);
        request.put("monthlyVehicleInsuranceRate", 0.0029166667);
        request.put("monthlyAdministrativeExpense", 10);
        request.put("referenceDiscountRate", 0.15);
        return request;
    }

    private Map<String, Object> buildFrontendDraft() {
        Map<String, Object> draft = new HashMap<>();
        draft.put("client", Map.of("documentNumber", "12345678", "fullName", "Carlos Mendoza"));
        draft.put("vehicle", Map.of("currency", "PEN", "vehiclePrice", 30000));
        draft.put("credit", Map.of("initialFeePercentage", 20, "balloonFeePercentage", 35, "termMonths", 48));
        draft.put("interest", Map.of("rateType", "TEA", "rateValuePercentage", 12.5, "paymentFrequency", "MONTHLY"));
        draft.put("gracePeriod", Map.of("type", "NONE", "months", 0));
        draft.put("financialAnalysis", Map.of("targetTirPercentage", 15));
        draft.put("costs", Map.of(
                "lifeInsuranceMonthlyRatePercentage", 0.05,
                "administrativeExpenses", 10,
                "vehicleInsuranceAnnualRatePercentage", 3.5));
        return draft;
    }
}
