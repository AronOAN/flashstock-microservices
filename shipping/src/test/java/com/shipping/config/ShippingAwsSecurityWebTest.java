package com.shipping.config;

import com.shipping.controllers.ShippingController;
import com.shipping.services.ShippingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("aws")
@WebMvcTest(value = ShippingController.class, properties = {
        "flashstock.cognito.issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test",
        "flashstock.cognito.client-id=flashstocktestclient",
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(AwsCognitoSecurityConfig.class)
class ShippingAwsSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private ShippingService service;
    @MockBean private JwtDecoder decoder;

    @Test
    void cookiesDoNotAuthorizeAndUserCannotModifyDelivery() throws Exception {
        mvc.perform(patch("/api/shipping/TRK-1/status/delivered")
                .cookie(new Cookie("JSESSIONID", "forged")))
                .andExpect(status().isUnauthorized());
        when(decoder.decode(anyString())).thenReturn(token("USER"));
        mvc.perform(get("/api/shipping/tracking/TRK-1").header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/shipping").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/shipping/TRK-1/status/delivered")
                .header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminBearerCanModifyDelivery() throws Exception {
        when(decoder.decode(anyString())).thenReturn(token("ADMIN"));
        mvc.perform(patch("/api/shipping/TRK-1/status/delivered")
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    private Jwt token(String role) {
        return Jwt.withTokenValue("test").header("alg", "RS256").subject("stable-sub")
                .claim("token_use", "access").claim("cognito:groups", List.of(role)).build();
    }
}
