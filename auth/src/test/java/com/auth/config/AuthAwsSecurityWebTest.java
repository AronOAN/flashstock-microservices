package com.auth.config;

import com.auth.controllers.AuthController;
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
    @MockBean private JwtDecoder decoder;

    @Test
    void publicProvidersRequireNoTokenButCookiesCannotAuthorizeMe() throws Exception {
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", "forged")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/me")).andExpect(status().isForbidden());
    }

    @Test
    void userTokenCanReadIdentityButNotAdminMetrics() throws Exception {
        when(decoder.decode(anyString())).thenReturn(Jwt.withTokenValue("test")
                .header("alg", "RS256").subject("user-1")
                .claim("token_use", "access").claim("cognito:groups", List.of("USER"))
                .build());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/metrics").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
    }
}
