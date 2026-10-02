package com.inventory.config;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;

class CognitoAuthoritiesConverterTest {
    private Jwt token(List<String> groups) {
        return Jwt.withTokenValue("not-a-real-token")
            .header("alg", "RS256")
            .subject("stable-user-id")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("cognito:groups", groups)
            .claim("scope", "openid email")
            .build();
    }

    @Test void adminMapsToExistingRoleAdmin() {
        var authorities = new CognitoAuthoritiesConverter().convert(token(List.of("ADMIN"))).getAuthorities();
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("SCOPE_openid")));
    }

    @Test void unknownGroupCannotEscalate() {
        var authorities = new CognitoAuthoritiesConverter().convert(token(List.of("ROLE_ADMIN", "admin", "solicitudes-dev"))).getAuthorities();
        assertFalse(authorities.stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_")));
    }

    @Test void userGroupMapsOnlyToRoleUser() {
        var authorities = new CognitoAuthoritiesConverter().convert(token(List.of("USER"))).getAuthorities();
        assertTrue(authorities.stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        assertFalse(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test void missingGroupsDoesNotInventRoles() {
        Jwt jwt = Jwt.withTokenValue("synthetic-token")
            .header("alg", "RS256").subject("stable-user-id")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("scope", "openid").build();
        var authorities = new CognitoAuthoritiesConverter().convert(jwt).getAuthorities();
        assertFalse(authorities.stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_")));
        assertTrue(authorities.stream().anyMatch(a -> "SCOPE_openid".equals(a.getAuthority())));
    }
}
