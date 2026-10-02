package com.order.config;

import com.order.controllers.OrderController;
import com.order.services.OrderService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("aws")
@WebMvcTest(value = OrderController.class, properties = {
        "flashstock.cognito.issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test",
        "flashstock.cognito.client-id=flashstocktestclient",
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(AwsCognitoSecurityConfig.class)
class OrderAwsSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService service;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void cookieCannotClaimOrderButUserBearerCanCreateAndReadOwnHistory() throws Exception {
        mvc.perform(post("/api/orders").cookie(new Cookie("JSESSIONID", "forged"))
                .contentType("application/json").content("{\"sku\":\"SKU-1\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        when(decoder.decode(anyString())).thenReturn(token("user-1", "USER"));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", "Bearer user")
                .contentType("application/json").content("{\"sku\":\"SKU-1\",\"quantity\":1}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/my-history").header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
        verify(service).findMyOrderHistory("user-1", null);
    }

    @Test
    void adminBearerReadsOrdersButReceiptSendingIsDenied() throws Exception {
        when(decoder.decode(anyString())).thenReturn(token("admin-1", "ADMIN"));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/receipts/send-email").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }

    private Jwt token(String sub, String role) {
        return Jwt.withTokenValue("test").header("alg", "RS256").subject(sub)
                .claim("token_use", "access").claim("cognito:groups", List.of(role)).build();
    }
}
