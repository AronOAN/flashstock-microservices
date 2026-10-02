package com.auth.controllers;

import com.auth.common.ApiResponse;
import com.auth.dtos.SessionUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "adminEmail", "aron83353@gmail.com");
        ReflectionTestUtils.setField(controller, "legacyAdminEmailEnabled", false);
        ReflectionTestUtils.setField(controller, "googleClientId", "google-client-id");
        ReflectionTestUtils.setField(controller, "microsoftClientId", "disabled-microsoft-client-id");
    }

    @Test
    void meReturnsAnonymousPayloadWhenNotAuthenticated() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        ApiResponse<SessionUserResponse> response = controller.me(authentication);

        assertEquals("Sesion anonima", response.getMessage());
        assertFalse(response.getData().isAuthenticated());
        assertFalse(response.getData().isAdmin());
        assertEquals("Invitado", response.getData().getDisplayName());
        assertTrue(response.getData().getAuthorities().isEmpty());
    }

    @Test
    void meDoesNotGrantAdminFromEmailWhenRoleIsUser() {
        OAuth2User principal = mock(OAuth2User.class);
        Authentication authentication = new TestingAuthenticationToken(principal, "n/a", "ROLE_USER");
        authentication.setAuthenticated(true);
        when(principal.getAttributes()).thenReturn(Map.of(
                "email", "aron83353@gmail.com",
                "name", "Administrador"
        ));

        ApiResponse<SessionUserResponse> response = controller.me(authentication);

        assertEquals("Sesion activa", response.getMessage());
        assertTrue(response.getData().isAuthenticated());
        assertFalse(response.getData().isAdmin());
        assertEquals("aron83353@gmail.com", response.getData().getEmail());
        assertEquals("Administrador", response.getData().getDisplayName());
        assertEquals(List.of("ROLE_USER"), response.getData().getAuthorities());
    }

    @Test
    void meMarksAdminWhenRoleAdminIsGranted() {
        Authentication authentication = new TestingAuthenticationToken(
                "aron83353@gmail.com", "n/a", "ROLE_ADMIN", "ROLE_USER");
        authentication.setAuthenticated(true);
        ApiResponse<SessionUserResponse> response = controller.me(authentication);
        assertTrue(response.getData().isAuthenticated());
        assertTrue(response.getData().isAdmin());
        assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"), response.getData().getAuthorities());
    }

    @Test
    void providersReportConfiguredProviders() {
        ApiResponse<Map<String, Boolean>> response = controller.providers();

        assertEquals("Estado de proveedores de autenticacion", response.getMessage());
        assertTrue(response.getData().get("google"));
        assertFalse(response.getData().get("microsoft"));
    }
}