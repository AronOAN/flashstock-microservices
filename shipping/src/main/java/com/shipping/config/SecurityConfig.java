package com.shipping.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("!aws")
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SHIPPING_PATH = "/api/shipping/**";
    private static final String ORDERS_PATH = "/api/orders/**";
    private static final String INVENTORY_PATH = "/api/inventory/**";
    private static final String INDEX_PATH = "/index.html";


    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Local OAuth2 login uses a browser session: keep Spring's default CSRF protection.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").permitAll()
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/cart/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/orders/my-history").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/orders/customer-shipping", "/api/orders").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/shipping").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/api/shipping/tracking/**", "/api/shipping/*").authenticated()
                .requestMatchers(HttpMethod.POST, SHIPPING_PATH).hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.POST, ORDERS_PATH).denyAll()
                .requestMatchers(HttpMethod.GET, "/api/payments/google-pay/config", "/api/payments/deuna/config").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/google-pay/authorize", "/api/payments/deuna/attempts", "/api/payments/deuna/webhook").permitAll()
                .requestMatchers("/api/payments/**").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.POST, "/api/receipts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/receipts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/coupons/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/coupons/**").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.POST, INVENTORY_PATH).hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.PATCH, INVENTORY_PATH, ORDERS_PATH, SHIPPING_PATH).hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.PUT, INVENTORY_PATH, ORDERS_PATH, SHIPPING_PATH).hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.DELETE, INVENTORY_PATH, ORDERS_PATH, SHIPPING_PATH).hasRole(ADMIN_ROLE)
                .requestMatchers(
                    "/",
                    INDEX_PATH,
                    "/shop.html",
                    "/cart.html",
                    "/chackout.html",
                    "/contact.html",
                    "/login",
                    "/login.html",
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/css/**",
                    "/js/**",
                    "/img/**",
                    "/lib/**",
                    "/api/auth/me",
                    "/api/maps/config",
                    "/api/auth/providers",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, INVENTORY_PATH).permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .authorizationEndpoint(authorization -> authorization.authorizationRequestResolver(customAuthorizationRequestResolver))
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService))
                .failureHandler((request, response, exception) -> {
                    String rawMessage = exception != null && exception.getMessage() != null
                        ? exception.getMessage()
                        : "Error OAuth2 desconocido";
                    String safeMessage = rawMessage.length() > 600 ? rawMessage.substring(0, 600) : rawMessage;
                    String encodedMessage = URLEncoder.encode(safeMessage, StandardCharsets.UTF_8);
                    response.sendRedirect("/login?error=oauth2&message=" + encodedMessage);
                })
                .defaultSuccessUrl(INDEX_PATH, true))
            .logout(logout -> logout
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl(INDEX_PATH));

        return http.build();
    }
}
