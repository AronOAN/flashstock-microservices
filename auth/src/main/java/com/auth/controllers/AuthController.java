package com.auth.controllers;

import com.auth.common.ApiResponse;
import com.auth.dtos.SessionUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AuthController {

    @Value("${app.security.admin-email:}")
    private String adminEmail;

    @Value("${app.security.legacy-admin-email-enabled:false}")
    private boolean legacyAdminEmailEnabled;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id:}")
    private String microsoftClientId;

    @GetMapping("/me")
    public ApiResponse<SessionUserResponse> me(Authentication authentication) {
        boolean isAuthenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (!isAuthenticated) {
            return ApiResponse.<SessionUserResponse>builder()
                    .message("Sesion anonima")
                    .data(SessionUserResponse.builder()
                            .authenticated(false)
                            .admin(false)
                            .email(null)
                            .displayName("Invitado")
                            .authorities(Collections.emptyList())
                            .build())
                    .build();
        }

        List<String> authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        boolean isAdminByRole = authorities.contains("ROLE_ADMIN");
        Identity identity = resolveIdentity(authentication);
        String email = identity.email();
        String displayName = identity.displayName();

        boolean isAdminByEmail = legacyAdminEmailEnabled && adminEmail != null
            && !adminEmail.isBlank()
            && email != null
            && email.equalsIgnoreCase(adminEmail);

        boolean isAdmin = isAdminByRole || isAdminByEmail;

        return ApiResponse.<SessionUserResponse>builder()
                .message("Sesion activa")
                .data(SessionUserResponse.builder()
                        .authenticated(true)
                        .admin(isAdmin)
                        .email(email)
                        .displayName(displayName)
                        .authorities(authorities)
                        .build())
                .build();
    }

    private Identity resolveIdentity(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String name = jwt.getClaimAsString("username");
            return new Identity(jwt.getClaimAsString("email"),
                    name == null || name.isBlank() ? "Usuario" : name);
        }
        if (principal instanceof OAuth2User oauth2User) {
            Map<String, Object> attributes = oauth2User.getAttributes();
            String email = firstString(attributes, "email", "mail", "userPrincipalName", "preferred_username");
            String name = firstString(attributes, "name");
            return new Identity(email == null ? authentication.getName() : email,
                    name == null ? authentication.getName() : name);
        }
        return new Identity(authentication.getName(), authentication.getName());
    }

    private String firstString(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value instanceof String text && !text.isBlank()) return text;
        }
        return null;
    }

    private record Identity(String email, String displayName) { }

    @GetMapping("/providers")
    public ApiResponse<Map<String, Boolean>> providers() {
        boolean googleConfigured = isProviderConfigured(googleClientId, "replace-with-your-google-client-id", "disabled-google-client-id");
        boolean microsoftConfigured = isProviderConfigured(microsoftClientId, "replace-with-your-microsoft-client-id", "disabled-microsoft-client-id");

        return ApiResponse.<Map<String, Boolean>>builder()
                .message("Estado de proveedores de autenticacion")
                .data(Map.of(
                        "google", googleConfigured,
                        "microsoft", microsoftConfigured
                ))
                .build();
    }

    private boolean isProviderConfigured(String clientId, String placeholder, String disabledValue) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }

        String normalized = clientId.trim();
        return !normalized.equalsIgnoreCase(placeholder)
                && !normalized.equalsIgnoreCase(disabledValue);
    }
}
