package com.bup.gridwise.service;

import com.bup.gridwise.dto.request.BatteryConfig;
import com.bup.gridwise.dto.request.HourEntry;
import com.bup.gridwise.dto.request.ScenarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleValidationServiceTest {

    private ScheduleValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ScheduleValidationService();
    }

    private List<HourEntry> createValid24Hours() {
        List<HourEntry> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(new HourEntry(h, 100.0, 20.0, 10.0));
        }
        return hours;
    }

    private BatteryConfig createValidBattery() {
        return new BatteryConfig(500.0, 200.0, 50.0, 100.0, 100.0);
    }

    @Test
    void testValidRequestPasses() {
        ScenarioRequest request = new ScenarioRequest("SCENARIO-VALID", List.of("Note 1"), createValid24Hours(), createValidBattery());
        assertDoesNotThrow(() -> validationService.validateScenarioRequest(request));
    }

    @Test
    void testNullPayloadThrows() {
        assertThrows(IllegalArgumentException.class, () -> validationService.validateScenarioRequest(null));
    }

    @Test
    void testDuplicateHourThrows() {
        List<HourEntry> hours = createValid24Hours();
        hours.set(23, new HourEntry(0, 100.0, 20.0, 10.0)); // Duplicate hour 0 instead of 23
        ScenarioRequest request = new ScenarioRequest("SCENARIO-DUP", List.of("Note 1"), hours, createValidBattery());
        assertThrows(IllegalArgumentException.class, () -> validationService.validateScenarioRequest(request));
    }

    @Test
    void testNegativeDemandThrows() {
        List<HourEntry> hours = createValid24Hours();
        hours.set(5, new HourEntry(5, -10.0, 20.0, 10.0));
        ScenarioRequest request = new ScenarioRequest("SCENARIO-NEG-DEMAND", List.of("Note 1"), hours, createValidBattery());
        assertThrows(IllegalArgumentException.class, () -> validationService.validateScenarioRequest(request));
    }

    @Test
    void testCapacityLessThanReserveThrows() {
        BatteryConfig battery = new BatteryConfig(40.0, 200.0, 50.0, 100.0, 100.0); // Capacity 40 < Reserve 50
        ScenarioRequest request = new ScenarioRequest("SCENARIO-CAPACITY-ERR", List.of("Note 1"), createValid24Hours(), battery);
        assertThrows(IllegalArgumentException.class, () -> validationService.validateScenarioRequest(request));
    }

    @Test
    void testInitialEnergyExceedsCapacityThrows() {
        BatteryConfig battery = new BatteryConfig(500.0, 600.0, 50.0, 100.0, 100.0); // Initial 600 > Capacity 500
        ScenarioRequest request = new ScenarioRequest("SCENARIO-INITIAL-ERR", List.of("Note 1"), createValid24Hours(), battery);
        assertThrows(IllegalArgumentException.class, () -> validationService.validateScenarioRequest(request));
    }
}
