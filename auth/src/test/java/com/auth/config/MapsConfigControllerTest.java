package com.auth.config;

import com.auth.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapsConfigControllerTest {

    private MapsConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new MapsConfigController();
        ReflectionTestUtils.setField(controller, "googleApiKey", "  test-map-key  ");
    }

    @Test
    void returnsPublicMapConfigWithTrimmedKey() {
        var response = controller.getPublicMapConfig();

        assertEquals(200, response.getStatusCodeValue());
        ApiResponse<Map<String, String>> body = response.getBody();
        assertEquals("Configuracion de mapas", body.getMessage());
        assertEquals("google", body.getData().get("provider"));
        assertEquals("test-map-key", body.getData().get("googleApiKey"));
    }
}