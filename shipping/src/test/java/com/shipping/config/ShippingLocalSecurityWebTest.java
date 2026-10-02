package com.shipping.config;

import com.shipping.controllers.ShippingController;
import com.shipping.services.ShippingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@ActiveProfiles("test")
@WebMvcTest(value = ShippingController.class, properties = {
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(SecurityConfig.class)
class ShippingLocalSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private ShippingService service;
    @MockBean private CustomOAuth2UserService oauthUserService;
    @MockBean private CustomOidcUserService oidcUserService;
    @MockBean private OAuth2AuthorizationRequestResolver oauthRequestResolver;

    @Test
    void browserSessionRequiresLoginAndPostWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(get("/api/shipping/tracking/TRK-1")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/shipping").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/login/oauth2/code/google").param("error", "access_denied"))
                .andExpect(redirectedUrlPattern("/login?error=oauth2&message=**"));
    }

    @Test
    void validBrowserCsrfStillRequiresAdminRoleForShipmentCreation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        var token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token");
        var response = new MockHttpServletResponse();
        new HttpSessionCsrfTokenRepository().saveToken(token, request, response);
        new XorCsrfTokenRequestAttributeHandler().handle(request, response, () -> token);
        String masked = ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken("local", "n/a", "ROLE_USER"));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mvc.perform(post("/api/shipping").session(session).header("X-CSRF-TOKEN", masked)
                .contentType("application/json").content("{\"orderNumber\":\"ORD-1\",\"carrier\":\"DHL\"}"))
                .andExpect(status().isForbidden());

        context.setAuthentication(new TestingAuthenticationToken("local-admin", "n/a", "ROLE_ADMIN"));
        mvc.perform(post("/api/shipping").session(session).header("X-CSRF-TOKEN", masked)
                .contentType("application/json").content("{\"orderNumber\":\"ORD-1\",\"carrier\":\"DHL\"}"))
                .andExpect(status().isOk());
    }
}
