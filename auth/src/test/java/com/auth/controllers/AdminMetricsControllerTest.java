package com.auth.controllers;

import com.auth.common.ApiResponse;
import com.auth.dtos.AdminMetricsResponse;
import com.auth.services.admin.AdminMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminMetricsControllerTest {

    private AdminMetricsService metricsService;
    private AdminMetricsController controller;

    @BeforeEach
    void setUp() {
        metricsService = mock(AdminMetricsService.class);
        controller = new AdminMetricsController(metricsService);
    }

    @Test
    void metricsAllowsAdminByRole() {
        AdminMetricsResponse metrics = AdminMetricsResponse.builder()
                .timestamp("2026-06-09T10:00:00")
                .inventorySkuCount(3)
                .build();
        when(metricsService.getAdminMetrics()).thenReturn(metrics);

        Authentication authentication = new TestingAuthenticationToken("someone@flashstock.com", "n/a", "ROLE_ADMIN");

        ApiResponse<AdminMetricsResponse> response = controller.metrics(authentication);

        assertEquals("Metricas administrativas en tiempo real", response.getMessage());
        assertEquals(metrics, response.getData());
    }

    @Test
    void metricsRejectsMatchingEmailWithoutAdminRole() {
        Authentication authentication = mock(Authentication.class);
        OAuth2User principal = mock(OAuth2User.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(authentication.getName()).thenReturn("fallback@flashstock.com");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttributes()).thenReturn(Map.of("mail", "aron83353@gmail.com"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.metrics(authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(metricsService);
    }

    @Test
    void metricsRejectsNonAdmin() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller.metrics(authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
}
