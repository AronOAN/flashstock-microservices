package com.shipping.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthEmailResolver {

    private AuthEmailResolver() {
    }

    public static String resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String email = jwtAuth.getToken().getClaimAsString("email");
            // Cognito access tokens do NOT guarantee an email claim. Do not treat sub as email.
            return isNonBlank(email) ? email : null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            String email = claim(oauth2User, "email");
            if (isNonBlank(email)) {
                return email;
            }

            String mail = claim(oauth2User, "mail");
            if (isNonBlank(mail)) {
                return mail;
            }

            String upn = claim(oauth2User, "userPrincipalName");
            if (isNonBlank(upn)) {
                return upn;
            }

            String preferredUsername = claim(oauth2User, "preferred_username");
            if (isNonBlank(preferredUsername)) {
                return preferredUsername;
            }
        }

        String name = authentication.getName();
        return isNonBlank(name) ? name : null;
    }

    private static String claim(OAuth2User user, String key) {
        Object value = user.getAttributes().get(key);
        return value instanceof String text ? text : null;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}