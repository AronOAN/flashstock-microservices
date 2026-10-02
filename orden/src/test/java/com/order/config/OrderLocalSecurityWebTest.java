package com.order.config;

import com.order.controllers.OrderController;
import com.order.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@ActiveProfiles("test")
@WebMvcTest(value = OrderController.class, properties = {
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(SecurityConfig.class)
class OrderLocalSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService orders;
    @MockitoBean private CustomOAuth2UserService oauthUserService;
    @MockitoBean private CustomOidcUserService oidcUserService;
    @MockitoBean private OAuth2AuthorizationRequestResolver oauthRequestResolver;

    @Test
    void localBrowserSessionCannotClaimCognitoOrderAndUnsafePostNeedsCsrf() throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken("local", "n/a", "ROLE_USER"));
        mvc.perform(get("/api/orders/my-history")
                .sessionAttr(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/login/oauth2/code/google").param("error", "access_denied"))
                .andExpect(redirectedUrlPattern("/login?error=oauth2&message=**"));
    }
}
