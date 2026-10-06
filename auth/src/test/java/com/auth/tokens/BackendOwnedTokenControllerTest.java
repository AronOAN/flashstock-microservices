package com.auth.tokens;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackendOwnedTokenControllerTest {
    private static final String SITE="https://flashstock.example";
    private static final String SECRET=Base64.getEncoder().encodeToString(new byte[32]);
    private TokenSessionService sessions; private FlashstockJwtService signer; private BackendOwnedTokenController endpoint; private HttpServletRequest request;
    @BeforeEach void init(){sessions=mock(TokenSessionService.class);signer=mock(FlashstockJwtService.class);endpoint=new BackendOwnedTokenController(sessions,signer,SITE,SECRET);request=mock(HttpServletRequest.class);when(request.getHeader("Origin")).thenReturn(SITE);when(request.getHeader("X-Flashstock-BFF-Secret")).thenReturn(SECRET);}
    private Jwt cognito(String sub,List<String> groups){return Jwt.withTokenValue("verified-cognito-token").header("alg","RS256").subject(sub).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("cognito:groups",groups).build();}
    @Test void rejectsMissingServerSecret(){when(request.getHeader("X-Flashstock-BFF-Secret")).thenReturn(null);var ex=assertThrows(ResponseStatusException.class,()->endpoint.exchange(cognito("user",List.of("USER")),request));assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());verifyNoInteractions(sessions);}
    @Test void rejectsUserOutsideFlashstockGroups(){var ex=assertThrows(ResponseStatusException.class,()->endpoint.exchange(cognito("user",List.of("OTHER")),request));assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());verifyNoInteractions(sessions);}
    @Test void createsCookiesForVerifiedUser(){Jwt user=cognito("user",List.of("USER"));when(sessions.exchange(user)).thenReturn(new TokenSessionService.TokenPair("signed-access","signed-refresh","Bearer",900,604800));var response=endpoint.exchange(user,request);assertEquals(HttpStatus.OK,response.getStatusCode());List<String> cookies=response.getHeaders().get(HttpHeaders.SET_COOKIE);assertNotNull(cookies);assertEquals(2,cookies.size());assertTrue(cookies.stream().allMatch(s->s.contains("HttpOnly")&&s.contains("Secure")&&s.contains("SameSite=Lax")));}
    @Test void createsCookiesForVerifiedAdmin(){Jwt admin=cognito("admin",List.of("ADMIN"));when(sessions.exchange(admin)).thenReturn(new TokenSessionService.TokenPair("signed-access","signed-refresh","Bearer",900,604800));assertEquals(HttpStatus.OK,endpoint.exchange(admin,request).getStatusCode());}
    @Test void authorizeRejectsCrossSubject(){Jwt user=cognito("real-cognito-sub",List.of("USER"));when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("__Host-flashstock-issued-access","own-rs256-token")});JwtDecoder decoder=mock(JwtDecoder.class);when(signer.accessDecoder()).thenReturn(decoder);when(decoder.decode("own-rs256-token")).thenReturn(Jwt.withTokenValue("own-rs256-token").header("alg","RS256").subject("different-sub").claim("roles",List.of("ROLE_USER")).build());var ex=assertThrows(ResponseStatusException.class,()->endpoint.authorize(user,request));assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());}
}
