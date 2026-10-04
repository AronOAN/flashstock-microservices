package com.auth.config;

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
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import java.util.Set;
import org.springframework.security.web.csrf.CsrfFilter;


@Configuration
@Profile("aws")
public class AwsCognitoSecurityConfig {


    private static final String role = "ADMIN";

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
    SecurityFilterChain cognitoSecurityFilterChain(
            HttpSecurity http,
            @Value("${flashstock.tokens.enabled:false}") boolean localTokensEnabled) throws Exception {
        // The general HTTP Authorization Bearer is ALWAYS a Cognito access token.
        // FlashStock-issued JWTs are verified only in the narrowly scoped backend-owned controller.
        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .oauth2Login(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/maps/config").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/me").hasRole(role);
                if (localTokensEnabled) {
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/browser/exchange", "/api/auth/browser/authorize", "/api/auth/browser/refresh").hasRole(role)
                        .requestMatchers(HttpMethod.POST, "/api/auth/browser/revoke").permitAll();
                }
                auth.requestMatchers("/api/admin/**").hasRole(role)
                    .anyRequest().denyAll();
            });
        if (localTokensEnabled) {

            http.csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> {

                // Conserva la protección CSRF predeterminada de Spring Security.
                boolean requiresCsrf =
                        CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request);

                // Excepción limitada a los cuatro endpoints POST del BFF.
                boolean isTrustedBffEndpoint =
                        "POST".equals(request.getMethod())
                        && BFF_CSRF_EXEMPT_PATHS.contains(request.getServletPath());

                return requiresCsrf && !isTrustedBffEndpoint;
            }));
        }
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                jwt.jwtAuthenticationConverter(new CognitoAuthoritiesConverter())));
        return http.build();
    }
}
