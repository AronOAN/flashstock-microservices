package com.auth.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;


@Configuration
@Profile("aws")
@ConditionalOnProperty(name = "flashstock.bff.enabled",havingValue = "true")
public class BffSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain browserBffSecurity(
            HttpSecurity http
    ) throws Exception {

        var csrfRepository =
                new HttpSessionCsrfTokenRepository();

        csrfRepository.setHeaderName("X-CSRF-Token");

        return http
            .securityMatcher("/api/**")

            // CSRF obligatorio para solicitudes mutables.
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
            )

            // Sesión de servidor, no JWT del navegador.
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.IF_REQUIRED
                )
                .sessionFixation(fixation ->
                    fixation.changeSessionId()
                )
            )

            .securityContext(context -> context
                .securityContextRepository(
                    new HttpSessionSecurityContextRepository()
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Preflight no requerido en BFF same-origin.
                .requestMatchers(
                    HttpMethod.OPTIONS, "/api/**"
                ).denyAll()

                // Rutas públicas.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/catalog",
                    "/api/maps/config",
                    "/api/auth/session/csrf",
                    "/api/auth/session/pkce",
                    "/api/auth/session/callback"
                ).permitAll()

                // Autenticación: requieren CSRF.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/session/login",
                    "/api/auth/session/challenge",
                    "/api/auth/session/logout"
                ).permitAll()

                // Identidad.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/me"
                ).hasAnyRole("USER", "ADMIN")

                // Administración.
                .requestMatchers(
                    "/api/admin/**",
                    "/api/inventory",
                    "/api/inventory/**"
                ).hasRole("ADMIN")

                // Envíos administrativos.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/shipping"
                ).hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/shipping",
                    "/api/shipping/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/shipping/**"
                ).hasRole("ADMIN")

                // Seguimiento; la propiedad se valida
                // también en el microservicio Shipping.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/shipping/tracking/**"
                ).hasAnyRole("USER", "ADMIN")

                // Carrito, pedidos y boletas.
                .requestMatchers(
                    "/api/cart",
                    "/api/cart/**",
                    "/api/orders",
                    "/api/orders/**",
                    "/api/receipts/**"
                ).hasAnyRole("USER", "ADMIN")

                // Todo lo no autorizado explícitamente
                // se rechaza.
                .anyRequest().denyAll()
            )

            .build();
    }
}
