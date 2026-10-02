
package com.order.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthEmailResolverTest {

    private static final String TEST_EMAIL = "aro.acevedo@duocuc.cl";

    private Authentication oauth(
            Map<String, Object> attributes,
            String fallback
    ) {
        Authentication authentication = mock(Authentication.class);
        OAuth2User principal = mock(OAuth2User.class);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttributes()).thenReturn(attributes);
        when(authentication.getName()).thenReturn(fallback);

        return authentication;
    }

    private JwtAuthenticationToken jwt(String email) {

        Instant now = Instant.now();

        var builder = Jwt.withTokenValue("synthetic-test-token")
                .header("alg", "RS256")
                .subject("subject-id")
                .issuedAt(now.minusSeconds(20))
                .expiresAt(now.plusSeconds(120));

        if (email != null) {
            builder.claim("email", email);
        }

        Jwt token = builder.build();

        // Este constructor configura la autenticación como exitosa.
        return new JwtAuthenticationToken(token, List.of());
    }

    @Test
    void doesNotResolveUnauthenticatedUser() {

        assertNull(AuthEmailResolver.resolve(null));

        Authentication authentication = mock(Authentication.class);

        when(authentication.isAuthenticated()).thenReturn(false);

        assertNull(AuthEmailResolver.resolve(authentication));
    }

    @Test
    void resolvesJwtEmailButNeverTreatsSubjectAsEmail() {

        JwtAuthenticationToken authentication = jwt(TEST_EMAIL);

        assertTrue(authentication.isAuthenticated());

        assertEquals(
                TEST_EMAIL,
                AuthEmailResolver.resolve(authentication)
        );

        assertNull(AuthEmailResolver.resolve(jwt(null)));
        assertNull(AuthEmailResolver.resolve(jwt("  ")));
    }

    @Test
    void resolvesOAuth2EmailPriorityAndFallbacks() {

        assertEquals(
                TEST_EMAIL,
                AuthEmailResolver.resolve(
                        oauth(
                                Map.of(
                                        "email", TEST_EMAIL,
                                        "mail", TEST_EMAIL
                                ),
                                "fallback"
                        )
                )
        );

        assertEquals(
                "mail@flashstock.com",
                AuthEmailResolver.resolve(
                        oauth(
                                Map.of(
                                        "email", " ",
                                        "mail", "mail@flashstock.com"
                                ),
                                "fallback"
                        )
                )
        );

        assertEquals(
                TEST_EMAIL,
                AuthEmailResolver.resolve(
                        oauth(
                                Map.of("userPrincipalName", TEST_EMAIL),
                                "fallback"
                        )
                )
        );

        assertEquals(
                TEST_EMAIL,
                AuthEmailResolver.resolve(
                        oauth(
                                Map.of("preferred_username", TEST_EMAIL),
                                "fallback"
                        )
                )
        );

        assertEquals(
                TEST_EMAIL,
                AuthEmailResolver.resolve(
                        oauth(Map.of(), TEST_EMAIL)
                )
        );
    }

    @Test
    void rejectsBlankFallback() {

        assertNull(
                AuthEmailResolver.resolve(
                        oauth(Map.of(), " ")
                )
        );
    }
}
