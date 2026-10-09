
package com.auth.bff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("aws")
@ConditionalOnProperty(
    name = "flashstock.bff.enabled",
    havingValue = "true"
)
public final class BffSessionService {

    private static final String GROUPS = "cognito:groups";

    // Validador existente de Cognito:
    // firma, issuer, expiración, client_id y token_use.
    private final JwtDecoder cognitoDecoder;

    // Debe utilizar la misma estrategia de persistencia
    // configurada para la SecurityFilterChain del BFF.
    private final HttpSessionSecurityContextRepository
        securityContextRepository =
            new HttpSessionSecurityContextRepository();

    private final HttpSessionCsrfTokenRepository
        csrfTokenRepository =
            new HttpSessionCsrfTokenRepository();

    public BffSessionService(
        @Qualifier("cognitoDecoder")
        JwtDecoder cognitoDecoder
    ) {
        this.cognitoDecoder = cognitoDecoder;
    }

    /**
     * Se invoca solamente después de que el backend
     * haya obtenido un access token desde Cognito.
     *
     * Nunca debe exponerse como un endpoint que permita
     * definir roles, sub o identidad mediante JSON.
     */
    public BffPrincipal establishSession(
        String cognitoAccessToken,
        HttpServletRequest request,
        HttpServletResponse response
    ) {

        if (
            cognitoAccessToken == null ||
            cognitoAccessToken.isBlank() ||
            cognitoAccessToken.length() > 16384
        ) {
            throw unauthorized();
        }

        final Jwt verifiedJwt;

        try {
            // La autenticidad del JWT se valida en Java.
            verifiedJwt = cognitoDecoder.decode(
                cognitoAccessToken
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw unauthorized();
        }

        if (!"access".equals(
            verifiedJwt.getClaimAsString("token_use")
        )) {
            throw unauthorized();
        }

        String sub = verifiedJwt.getSubject();

        if (
            sub == null ||
            sub.isBlank() ||
            sub.length() > 128
        ) {
            throw unauthorized();
        }

        // Los roles proceden exclusivamente de
        // los claims firmados por Cognito.
        List<String> groups =
            verifiedJwt.getClaimAsStringList(GROUPS);

        if (
            groups == null ||
            (
                !groups.contains("USER") &&
                !groups.contains("ADMIN")
            )
        ) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Usuario sin permisos FlashStock"
            );
        }

        List<GrantedAuthority> authorities =
            new ArrayList<>();

        authorities.add(
            new SimpleGrantedAuthority("ROLE_USER")
        );

        if (groups.contains("ADMIN")) {
            authorities.add(
                new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }

        // El access token de Cognito normalmente no
        // incluye email. No inventar uno.
        String email = verifiedJwt.getClaimAsBoolean(
            "email_verified"
        ) == Boolean.TRUE
            ? verifiedJwt.getClaimAsString("email")
            : null;

        String displayName = verifiedJwt.getClaimAsString(
            "username"
        );

        if (
            displayName == null ||
            displayName.isBlank() ||
            displayName.length() > 120
        ) {
            displayName = "Usuario";
        }

        BffPrincipal principal = new BffPrincipal(
            sub,
            email,
            displayName
        );

        // ============================================
        // NUEVA SESIÓN
        // ============================================

        // Se descarta cualquier sesión anterior.
        // Esto evita conservar una autenticación vieja
        // o reutilizar el identificador previo al login.
        HttpSession previousSession =
            request.getSession(false);

        if (previousSession != null) {
            previousSession.invalidate();
        }

        request.getSession(true);

        // ============================================
        // SECURITY CONTEXT
        // ============================================

        var authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                authorities
            );

        SecurityContext context =
            SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        // En Spring Security moderno no basta con
        // SecurityContextHolder.setContext().
        securityContextRepository.saveContext(
            context,
            request,
            response
        );

        // El token anterior no debe seguir siendo
        // el token CSRF de la sesión autenticada.
        csrfTokenRepository.saveToken(
            null,
            request,
            response
        );

        return principal;
    }

    /**
     * Invalida únicamente la sesión local del BFF.
     *
     * La revocación de las credenciales almacenadas
     * del lado del servidor debe hacerse antes de
     * llamar a este método.
     */
    public void invalidateLocalSession(
        HttpServletRequest request,
        HttpServletResponse response
    ) {

        csrfTokenRepository.saveToken(
            null,
            request,
            response
        );

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Autenticación inválida"
        );
    }
}
