package com.order.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerifiedCustomerTest {
    @Test
    void deniesAnonymousAndIdTokenEvenWhenTheyClaimAnAdminGroup() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> VerifiedCustomer.sub(null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> VerifiedCustomer.sub(new TestingAuthenticationToken("admin", "x", "ROLE_ADMIN")))
                .getStatusCode());
        JwtAuthenticationToken id = token("id", "owner", "ROLE_ADMIN", null, null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> VerifiedCustomer.sub(id)).getStatusCode());
        assertFalse(VerifiedCustomer.isAdmin(id));
    }

    @Test
    void deniesBlankSubjectOrAccessTokenWithoutUserGroup() {
        assertThrows(ResponseStatusException.class,
                () -> VerifiedCustomer.sub(token("access", "", "ROLE_USER", null, null)));
        assertThrows(ResponseStatusException.class,
                () -> VerifiedCustomer.sub(token("access", "owner", "ROLE_OTHER", null, null)));
        assertFalse(VerifiedCustomer.isAdmin(token("access", "owner", "ROLE_USER", null, null)));
    }

    @Test
    void ownerComesFromSubAndEmailRequiresVerifiedBooleanClaim() {
        JwtAuthenticationToken owner = token("access", "immutable-sub", "ROLE_USER",
                " owner@example.test ", true);
        assertEquals("immutable-sub", VerifiedCustomer.sub(owner));
        assertEquals("owner@example.test", VerifiedCustomer.verifiedEmail(owner));
        assertNull(VerifiedCustomer.verifiedEmail(token("access", "owner", "ROLE_USER",
                "attacker@example.test", false)));
        assertNull(VerifiedCustomer.verifiedEmail(token("access", "owner", "ROLE_USER",
                " ", true)));
        JwtAuthenticationToken admin = token("access", "admin", "ROLE_ADMIN", null, null);
        assertEquals("admin", VerifiedCustomer.sub(admin));
        assertTrue(VerifiedCustomer.isAdmin(admin));
    }

    private JwtAuthenticationToken token(String use, String sub, String role, String email, Boolean verified) {
        var builder = Jwt.withTokenValue("synthetic-token").header("alg", "RS256")
                .claim("token_use", use);
        if (sub != null) builder.subject(sub);
        if (email != null) builder.claim("email", email);
        if (verified != null) builder.claim("email_verified", verified);
        Jwt jwt = builder.build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }
}
