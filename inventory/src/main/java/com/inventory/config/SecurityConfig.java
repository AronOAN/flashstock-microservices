
package com.inventory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@Profile("!aws")
public class SecurityConfig {

    private static final String ADMIN_ROLE = "ADMIN";

    private static final String INVENTORY_ROOT = "/api/inventory";
    private static final String INVENTORY_SUBPATH = "/api/inventory/**";

    /**
     * Utiliza el mismo validador de Cognito que AWS.
     *
     * Comprueba:
     * - Firma mediante JWKS (NimbusJwtDecoder).
     * - Issuer y vencimiento.
     * - token_use = access.
     * - client_id esperado.
     */
    @Bean
    JwtDecoder cognitoDecoder(
            @Value("${flashstock.cognito.issuer}") String issuer,
            @Value("${flashstock.cognito.client-id}") String clientId
    ) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .build();

        decoder.setJwtValidator(
                AwsCognitoSecurityConfig.cognitoValidator(
                        issuer,
                        clientId
                )
        );

        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http
    ) throws Exception {

        return http

            // Only explicit Bearer credentials authenticate this stateless API.
            .csrf(csrf -> csrf.ignoringRequestMatchers(INVENTORY_ROOT, INVENTORY_SUBPATH))

            // Nunca utilizar HttpSession para persistir
            // el SecurityContext.
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

            // No guardar solicitudes para redirecciones.
            .requestCache(cache -> cache
                .requestCache(new NullRequestCache())
            )

            // Inventory no administra el login del navegador.
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .oauth2Login(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth

                // Preflight.
                .requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                // Health check del ALB.
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health"
                ).permitAll()

                // Toda la API interna de inventario:
                // exclusivamente usuarios del grupo ADMIN.
                .requestMatchers(
                    INVENTORY_ROOT,
                    INVENTORY_SUBPATH
                ).hasRole(ADMIN_ROLE)

                // Denegar rutas no declaradas.
                .anyRequest().denyAll()
            )

            // JWT de Cognito validado en cada petición.
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
