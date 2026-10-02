package com.inventory.config;

import com.inventory.controllers.InventoryController;
import com.inventory.services.InventoryService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(value = InventoryController.class, properties = {
        "flashstock.cognito.issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test",
        "flashstock.cognito.client-id=flashstocktestclient",
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(SecurityConfig.class)
class InventoryLocalSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private InventoryService service;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void cookiesCannotAuthorizeAndOnlyAdminBearerCanChangeStock() throws Exception {
        mvc.perform(delete("/api/inventory/SKU-1").cookie(new Cookie("JSESSIONID", "forged")))
                .andExpect(status().isUnauthorized());
        when(decoder.decode(anyString())).thenReturn(Jwt.withTokenValue("user")
                .header("alg", "RS256").subject("user-1").claim("cognito:groups", List.of("USER"))
                .build());
        mvc.perform(get("/api/inventory").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/inventory/SKU-1").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        when(decoder.decode(anyString())).thenReturn(Jwt.withTokenValue("admin")
                .header("alg", "RS256").subject("admin-1").claim("cognito:groups", List.of("ADMIN"))
                .build());
        mvc.perform(delete("/api/inventory/SKU-1").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }
}
