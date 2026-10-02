package com.order.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * The identity used for ownership comes only from the validated Cognito access token.
 * Other authentication mechanisms in the local profile cannot create or claim AWS orders.
 */
public final class VerifiedCustomer {
    private VerifiedCustomer() {}

    public static String sub(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !authentication.isAuthenticated()
                || jwt.getToken().getSubject() == null
                || jwt.getToken().getSubject().isBlank()
                || !"access".equals(jwt.getToken().getClaimAsString("token_use"))
                || !hasRole(authentication, "ROLE_USER") && !hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identidad de cliente no verificada");
        }
        return jwt.getToken().getSubject();
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken
                && authentication.isAuthenticated()
                && "access".equals(((JwtAuthenticationToken) authentication).getToken().getClaimAsString("token_use"))
                && hasRole(authentication, "ROLE_ADMIN");
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    public static String verifiedEmail(Authentication authentication) {
        JwtAuthenticationToken jwt = (JwtAuthenticationToken) authentication;
        String email = jwt.getToken().getClaimAsString("email");
        return Boolean.TRUE.equals(jwt.getToken().getClaim("email_verified"))
                && email != null && !email.isBlank() ? email.trim() : null;
    }
}
