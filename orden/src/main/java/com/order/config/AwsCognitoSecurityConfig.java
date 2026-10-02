package com.order.config;

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

    @Bean
    JwtDecoder cognitoDecoder(@Value("${flashstock.cognito.issuer}") String issuer,
                              @Value("${flashstock.cognito.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(cognitoValidator(issuer, clientId));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> cognitoValidator(String issuer, String clientId) {
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> accessTokenOnly = token -> {
            if (!"access".equals(token.getClaimAsString("token_use"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token required", null));
            }
            if (!clientId.equals(token.getClaimAsString("client_id"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid client", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(defaultValidator, accessTokenOnly);
    }

    @Bean
    SecurityFilterChain cognitoSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            // Orders accept only explicit Bearer tokens, never browser session cookies.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/orders", "/api/orders/**"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .oauth2Login(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/customer-shipping", "/api/orders").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/orders/my-history").hasAnyRole("USER", ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAnyRole("USER", ADMIN_ROLE)
                .requestMatchers(HttpMethod.POST, "/api/orders/{orderNumber}/confirm-received", "/api/orders").hasAnyRole("USER", ADMIN_ROLE)
                .requestMatchers(HttpMethod.PATCH, "/api/orders/**").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/receipts/from-orders").hasAnyRole("USER", ADMIN_ROLE)
                .requestMatchers("/api/receipts/**").denyAll()
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new CognitoAuthoritiesConverter())))
            .build();
    }
}
