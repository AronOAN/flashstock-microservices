package com.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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

@Configuration
@Profile("aws")
public class AwsCognitoSecurityConfig {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String API_SUBPATH = "/api/orders/**";
    @Bean
    JwtDecoder cognitoDecoder(@Value("${flashstock.cognito.issuer}") String issuer,
                              @Value("${flashstock.cognito.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(cognitoValidator(issuer, clientId));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> cognitoValidator(String issuer, String clientId) {
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> accessTokenOnly = token -> {
            if (!"access".equals(token.getClaimAsString("token_use"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token required", null));
            }
            if (!clientId.equals(token.getClaimAsString("client_id"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid client", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(standard, accessTokenOnly);
    }

    @Bean
    SecurityFilterChain cognitoSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())  // Stateless Bearer API; tokens stay on the trusted Next.js BFF.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/customer-shipping", "/api/orders").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/orders/my-history").authenticated()
                .requestMatchers(HttpMethod.GET, API_SUBPATH).authenticated()
                .requestMatchers(HttpMethod.POST, API_SUBPATH, "/api/orders").authenticated()
                .requestMatchers(HttpMethod.PATCH, API_SUBPATH).hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/receipts/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/receipts/**").authenticated()
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new CognitoAuthoritiesConverter())))
            .build();
    }
}
