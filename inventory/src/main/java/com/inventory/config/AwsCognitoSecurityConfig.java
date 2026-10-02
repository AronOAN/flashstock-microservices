
package com.inventory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@Profile("aws")
public class AwsCognitoSecurityConfig {

    private static final String ADMIN_ROLE = "ADMIN";

    private static final String INVENTORY_ROOT = "/api/inventory";
    private static final String INVENTORY_SUBPATH = "/api/inventory/**";

    @Bean
    JwtDecoder cognitoDecoder(
            @Value("${flashstock.cognito.issuer}") String issuer,
            @Value("${flashstock.cognito.client-id}") String clientId
    ) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .build();

        decoder.setJwtValidator(
                cognitoValidator(issuer, clientId)
        );

        return decoder;
    }

    /**
     * Valida issuer, timestamps y atributos de
     * un Cognito Access Token.
     *
     * No acepta ID Tokens como credenciales de API.
     */
    static OAuth2TokenValidator<Jwt> cognitoValidator(
            String issuer,
            String clientId
    ) {

        OAuth2TokenValidator<Jwt> standard =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> accessTokenOnly = token -> {

            if (!"access".equals(
                    token.getClaimAsString("token_use")
            )) {

                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                        "invalid_token",
                        "Access token required",
                        null
                    )
                );
            }

            if (!clientId.equals(
                    token.getClaimAsString("client_id")
            )) {

                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                        "invalid_token",
                        "Invalid client",
                        null
                    )
                );
            }

            return OAuth2TokenValidatorResult.success();
        };

        return new DelegatingOAuth2TokenValidator<>(
                standard,
                accessTokenOnly
        );
    }

    @Bean
    // S4502 reviewed: Inventory authenticates only explicit Cognito Bearer tokens.
    // No HTTP session, browser login, Basic auth or cookie is an API credential.
    // The Next.js BFF checks Origin before attaching Authorization on mutations.
    // InventoryAwsSecurityWebTest rejects cookie-only writes and unauthorized roles.
    @SuppressWarnings("java:S4502")
    SecurityFilterChain cognitoSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http

            // Only explicit Bearer credentials authenticate this stateless API.
            .csrf(csrf -> csrf.ignoringRequestMatchers(INVENTORY_ROOT, INVENTORY_SUBPATH))

            // No crear ni utilizar sesiones HTTP
            // para almacenar autenticaciones.
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .securityContext(context -> context
                .securityContextRepository(
                    new NullSecurityContextRepository()
                )
            )

            .requestCache(cache -> cache
                .requestCache(new NullRequestCache())
            )

            // Deshabilitar mecanismos de login basados
            // en formulario, sesión o navegador.
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .oauth2Login(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth

                // OPTIONS: preflight.
                .requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                // Health check del ALB.
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health"
                ).permitAll()

                // API administrativa de Inventory.
                .requestMatchers(
                    INVENTORY_ROOT,
                    INVENTORY_SUBPATH
                ).hasRole(ADMIN_ROLE)

                // Mínimo privilegio.
                .anyRequest().denyAll()
            )

            // Resource Server con JWT de Cognito.
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(
                        new CognitoAuthoritiesConverter()
                    )
                )
            )

            .build();
    }
}
