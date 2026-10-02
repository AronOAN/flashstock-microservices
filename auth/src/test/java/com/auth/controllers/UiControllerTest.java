package com.auth.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiControllerTest {

    private final UiController controller = new UiController();

    @Test
    void rootForwardsToStaticIndex() {
        assertEquals("forward:/index.html", controller.root());
    }

    @Test
    void loginRedirectsAuthenticatedUsers() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);

        assertEquals("redirect:/index.html", controller.login(authentication));
    }

    @Test
    void loginForwardsAnonymousUsers() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertEquals("forward:/login.html", controller.login(authentication));
    }

    @Test
    void adminRoutesForwardToStaticHtml() {
        assertEquals("forward:/admin/inventory.html", controller.adminInventory());
        assertEquals("forward:/admin/dashboard.html", controller.adminDashboard());
        assertEquals("forward:/admin/shipments.html", controller.adminShipments());
        assertEquals("forward:/order-status.html", controller.orderStatus());
    }
}