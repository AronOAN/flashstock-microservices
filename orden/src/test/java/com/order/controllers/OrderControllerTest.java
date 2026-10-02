package com.order.controllers;

import com.order.dtos.OrderRequest;
import com.order.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderControllerTest {
    private OrderService service;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        service = mock(OrderService.class);
        controller = new OrderController(service);
    }

    @Test
    void createIgnoresUnverifiedEmailAndAssignsVerifiedSubject() {
        OrderRequest request = new OrderRequest();
        request.setCustomerEmail("other-person@example.com");
        controller.create(request, token("owner-1", "access", "ROLE_USER"));
        verify(service).create(request, "owner-1", null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void userReadsAndConfirmsOnlyUnderTheirSubject() {
        Authentication owner = token("owner-1", "access", "ROLE_USER");
        controller.getByOrderNumber("ORD-1", owner);
        controller.getMyHistory(owner, null);
        controller.confirmReceived(owner, "ORD-1");
        verify(service).findByOrderNumber("ORD-1", "owner-1", false);
        verify(service).findMyOrderHistory("owner-1", null);
        verify(service).confirmReceived("ORD-1", "owner-1");
    }

    @Test
    void adminReadsAndChangesStatusByVerifiedRole() {
        Authentication admin = token("admin-1", "access", "ROLE_ADMIN");
        controller.getAll(admin);
        controller.getByOrderNumber("ORD-1", admin);
        controller.updateStatus("ORD-1", "enviado", admin);
        verify(service).findAll();
        verify(service).findByOrderNumber("ORD-1", "admin-1", true);
        verify(service).updateStatus("ORD-1", "enviado");
    }

    @Test
    void userCannotEnumerateOrChangeOrders() {
        Authentication user = token("owner-1", "access", "ROLE_USER");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.getAll(user)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus("ORD-1", "enviado", user)).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void idTokenAndMissingIdentityCannotClaimOrders() {
        Authentication idToken = token("owner-1", "id", "ROLE_USER");
        OrderRequest request = new OrderRequest();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.create(request, idToken)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.confirmReceived(null, "ORD-1")).getStatusCode());
        verifyNoInteractions(service);
    }

    private Authentication token(String sub, String tokenUse, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(sub).claim("token_use", tokenUse).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)), sub);
    }
}
