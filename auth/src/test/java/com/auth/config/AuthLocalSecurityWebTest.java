package com.auth.config;

import com.auth.controllers.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@ActiveProfiles("test")
@WebMvcTest(value = AuthController.class, properties = {
        "spring.security.oauth2.client.registration.google.client-id=disabled",
        "spring.security.oauth2.client.registration.google.client-secret=disabled"
})
@Import(SecurityConfig.class)
class AuthLocalSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private CustomOAuth2UserService oauthUserService;
    @MockBean private CustomOidcUserService oidcUserService;
    @MockBean private OAuth2AuthorizationRequestResolver oauthRequestResolver;

    @Test
    void providersArePublicButBrowserMutationsNeedCsrf() throws Exception {
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/metrics")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/cart/checkout")).andExpect(status().isForbidden());
        mvc.perform(get("/login/oauth2/code/google").param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?error=oauth2&message=**"));
    }
}
