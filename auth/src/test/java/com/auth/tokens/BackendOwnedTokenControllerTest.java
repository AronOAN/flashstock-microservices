package com.auth.tokens;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackendOwnedTokenControllerTest {
    private static final String SITE="https://flashstock.example";
    private static final String SECRET=Base64.getEncoder().encodeToString(new byte[32]);
    private TokenSessionService sessions;
    private FlashstockJwtService signer;
    private BackendOwnedTokenController endpoint;
    private HttpServletRequest request;

    @BeforeEach void init() {
        sessions=mock(TokenSessionService.class); signer=mock(FlashstockJwtService.class);
        endpoint=new BackendOwnedTokenController(sessions,signer,SITE,SECRET);
        request=mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn(SITE);
        when(request.getHeader("X-Flashstock-BFF-Secret")).thenReturn(SECRET);
    }
    private Jwt cognito(String sub,List<String> groups) {
        return Jwt.withTokenValue("verified-cognito-token").header("alg","RS256")
                .subject(sub).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("cognito:groups",groups).build();
    }
    @Test void rejectsMissingServerSecretEvenWithAdminCognitoJwt() {
        when(request.getHeader("X-Flashstock-BFF-Secret")).thenReturn(null);
        var exception=assertThrows(ResponseStatusException.class,()->endpoint.exchange(cognito("admin",List.of("ADMIN")),request));
        assertEquals(HttpStatus.FORBIDDEN,exception.getStatusCode());
        verifyNoInteractions(sessions);
    }
    @Test void rejectsUserWhoIsNotInCognitoAdminGroup() {
        var exception=assertThrows(ResponseStatusException.class,()->endpoint.exchange(cognito("user",List.of("USER")),request));
        assertEquals(HttpStatus.FORBIDDEN,exception.getStatusCode());
        verifyNoInteractions(sessions);
    }
    @Test void createsOnlyHttpOnlySecureCookiesForVerifiedAdministrator() {
        Jwt admin=cognito("admin",List.of("ADMIN"));
        when(sessions.exchange(admin)).thenReturn(new TokenSessionService.TokenPair("signed-access","signed-refresh","Bearer",900,604800));
        var response=endpoint.exchange(admin,request);
        assertEquals(HttpStatus.OK,response.getStatusCode());
        assertFalse(response.getBody().toString().contains("signed-access"));
        assertFalse(response.getBody().toString().contains("signed-refresh"));
        List<String> cookies=response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies); assertEquals(2,cookies.size());
        assertTrue(cookies.stream().allMatch(s->s.contains("HttpOnly")&&s.contains("Secure")&&s.contains("SameSite=Lax")));
    }
    @Test void authorizeRejectsCrossSubjectEvenWithValidSignatures() {
        Jwt admin=cognito("real-cognito-sub",List.of("ADMIN"));
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("__Host-flashstock-issued-access","own-rs256-token")});
        JwtDecoder decoder=mock(JwtDecoder.class);
        when(signer.accessDecoder()).thenReturn(decoder);
        when(decoder.decode("own-rs256-token")).thenReturn(Jwt.withTokenValue("own-rs256-token")
                .header("alg","RS256").subject("different-sub")
                .claim("roles",List.of("ROLE_ADMIN")).build());
        var exception=assertThrows(ResponseStatusException.class,()->endpoint.authorize(admin,request));
        assertEquals(HttpStatus.FORBIDDEN,exception.getStatusCode());
    }
}
