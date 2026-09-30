package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void healthReturnsOkStatus() {
        HealthController controller = new HealthController();

        var response = controller.health();

        assertEquals("OK", response.getBody().get("status"));
    }
}