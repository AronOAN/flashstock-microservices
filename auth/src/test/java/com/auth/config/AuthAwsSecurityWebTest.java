package com.auth.config;

import com.auth.controllers.AuthController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("aws")
@WebMvcTest(value = AuthController.class, properties = {
        "flashstock.cognito.issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test",
        "flashstock.cognito.client-id=flashstocktestclient",
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(AwsCognitoSecurityConfig.class)
class AuthAwsSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void cookieCannotAuthorizeMe() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", "forged")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/me")).andExpect(status().isForbidden());
    }

    @Test
    void userTokenCannotReadAdminOnlyIdentityOrMetrics() throws Exception {
        when(decoder.decode(anyString())).thenReturn(Jwt.withTokenValue("test")
                .header("alg", "RS256").subject("user-1")
                .claim("token_use", "access").claim("cognito:groups", List.of("USER"))
                .build());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/metrics").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCanReadIdentity() throws Exception {
        when(decoder.decode(anyString())).thenReturn(Jwt.withTokenValue("test-admin")
                .header("alg", "RS256").subject("cognito-admin")
                .claim("token_use", "access").claim("cognito:groups", List.of("ADMIN"))
                .build());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        // A removed AWS route must not remain accidentally usable even with an admin token.
        mvc.perform(get("/api/auth/providers").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }
}
