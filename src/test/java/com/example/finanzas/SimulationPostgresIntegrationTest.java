package com.example.finanzas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SimulationPostgresIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authRegisterAndCalculateSimulation() throws Exception {
        Map<String, String> register = Map.of(
                "fullName", "Carlos Mendoza",
                "email", "demo@crediauto.pe",
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

        Map<String, Object> simulation = Map.of(
                "client", Map.of("documentNumber", "12345678", "fullName", "Carlos Mendoza"),
                "vehicle", Map.of("currency", "PEN", "vehiclePrice", 30000),
                "credit", Map.of("initialFeePercentage", 20, "balloonFeePercentage", 35, "termMonths", 48),
                "interest", Map.of("rateType", "TEA", "rateValuePercentage", 12.5, "paymentFrequency", "MONTHLY"),
                "gracePeriod", Map.of("type", "NONE", "months", 0),
                "financialAnalysis", Map.of("targetTirPercentage", 15),
                "costs", Map.of(
                        "lifeInsuranceMonthlyRatePercentage", 0.05,
                        "administrativeExpenses", 10,
                        "vehicleInsuranceAnnualRatePercentage", 3.5));

        mockMvc.perform(post("/api/v1/simulations/calculate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simulation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.results.tceaPercentage").exists())
                .andExpect(jsonPath("$.results.schedule.length()").value(48));
    }
}
