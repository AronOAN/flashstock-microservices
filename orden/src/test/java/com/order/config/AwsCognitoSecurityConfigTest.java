
package com.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("aws")
public class AwsCognitoSecurityConfig {

    /**
     * Valida los claims de un Access Token emitido por el
     * Cognito User Pool configurado para FlashStock.
     *
     * También se utiliza directamente desde las pruebas unitarias.
     *
     * Nota: esta validación no sustituye la verificación criptográfica
     * de la firma JWT que realiza NimbusJwtDecoder.
     */
    static OAuth2TokenValidator<Jwt> cognitoValidator(
            String issuer,
            String clientId
    ) {

        OAuth2TokenValidator<Jwt> defaultValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> accessTokenOnly = token -> {

            // Nunca aceptar un ID Token como Access Token.
            if (!"access".equals(token.getClaimAsString("token_use"))) {

                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "Access token required",
                                null
                        )
                );
            }

            // El token debe pertenecer al App Client configurado.
            if (!clientId.equals(token.getClaimAsString("client_id"))) {

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
                defaultValidator,
                accessTokenOnly
        );
    }

    /**
     * Decoder de Cognito.
     *
     * Nimbus verifica la firma del JWT utilizando las claves públicas
     * del JWKS del User Pool.
     *
     * Posteriormente se validan issuer, timestamps, token_use y client_id.
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
                cognitoValidator(issuer, clientId)
        );

        return decoder;
    }

    /**
     * Seguridad HTTP de Orden en AWS.
     *
     * No utiliza sesiones tradicionales ni autenticación por formulario.
     * Cada solicitud debe presentar un Access Token válido.
     */
    @Bean
    SecurityFilterChain cognitoSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/orders/customer-shipping",
                                "/api/orders"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/orders/my-history"
                        ).hasAnyRole("USER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/orders/**"
                        ).hasAnyRole("USER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/orders/{orderNumber}/confirm-received",
                                "/api/orders"
                        ).hasAnyRole("USER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/orders/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/receipts/from-orders"
                        ).hasAnyRole("USER", "ADMIN")

                        // Envío de boletas deshabilitado hasta verificar
                        // autorización y propiedad del pedido.
                        .requestMatchers(
                                "/api/receipts/**"
                        ).denyAll()

                        .anyRequest().denyAll()
                )

                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        new CognitoAuthoritiesConverter()
                                )
                        )
                )

                .build();
    }
}
