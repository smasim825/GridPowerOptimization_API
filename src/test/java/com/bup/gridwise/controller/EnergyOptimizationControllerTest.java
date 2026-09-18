package com.bup.gridwise.controller;

import com.bup.gridwise.dto.request.BatteryConfig;
import com.bup.gridwise.dto.request.HourEntry;
import com.bup.gridwise.dto.request.ScenarioRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnergyOptimizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void testOptimizeEnergyEndpoint() throws Exception {
        List<HourEntry> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(new HourEntry(h, 200.0, 50.0, 10.0));
        }

        BatteryConfig battery = new BatteryConfig(500.0, 200.0, 50.0, 100.0, 100.0);
        ScenarioRequest request = new ScenarioRequest(
                "TEST-SCENARIO-01",
                List.of("Do not charge between 2 PM and 4 PM."),
                hours,
                battery
        );

        mockMvc.perform(post("/optimize-energy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario_id").value("TEST-SCENARIO-01"))
                .andExpect(jsonPath("$.hourly_plan.length()").value(24))
                .andExpect(jsonPath("$.total_cost_bdt").exists());
    }
}
