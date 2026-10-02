package com.order.controllers;

import com.order.common.ApiResponse;
import com.order.dtos.OrderCustomerShippingResponse;
import com.order.dtos.OrderRequest;
import com.order.dtos.OrderResponse;
import com.order.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;


import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;


class OrderControllerTest {

    private OrderService service;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        service = mock(OrderService.class);
        controller = new OrderController(service);
        ReflectionTestUtils.setField(controller, "adminEmail", "admin@flashstock.com");
    }

    @Test
    void getAllReturnsOrders() {
        List<OrderResponse> orders = List.of(sampleOrderResponse());
        when(service.findAll()).thenReturn(orders);

        var response = controller.getAll();

        assertEquals(orders, response.getBody().getData());
    }

    @Test
    void createUsesAuthenticatedEmailWhenCustomerEmailIsBlank() {
        OrderRequest request = sampleOrderRequest();
        request.setCustomerEmail(" ");
        Authentication authentication = authWithEmail("buyer@flashstock.com", false);
        OrderResponse order = sampleOrderResponse();
        when(service.create(any(OrderRequest.class))).thenReturn(order);

        var response = controller.create(request, authentication);

        assertEquals("Pedido creado", response.getBody().getMessage());
        assertEquals(order, response.getBody().getData());
        verify(service).create(request);
        assertEquals("buyer@flashstock.com", request.getCustomerEmail());
    }

    @Test
    void getByOrderNumberReturnsOrder() {
        OrderResponse order = sampleOrderResponse();
        when(service.findByOrderNumber("ORD-1")).thenReturn(order);

        var response = controller.getByOrderNumber("ORD-1");

        assertEquals(order, response.getBody().getData());
    }

    @Test
    void updateStatusDelegatesToService() {
        OrderResponse order = sampleOrderResponse();
        when(service.updateStatus("ORD-1", "enviado")).thenReturn(order);

        var response = controller.updateStatus("ORD-1", "enviado");

        assertEquals(order, response.getBody().getData());
    }

    @Test
    void getCustomerShippingAllowsAdminByRole() {
        List<OrderCustomerShippingResponse> rows = List.of(sampleCustomerShipping());
        when(service.findCustomerOrderShipping(null)).thenReturn(rows);

        var response = controller.getCustomerShipping(authWithRole("ROLE_ADMIN"), null);

        assertEquals(rows, response.getBody().getData());
    }

    @Test
    void getCustomerShippingAllowsAdminByEmail() {
        List<OrderCustomerShippingResponse> rows = List.of(sampleCustomerShipping());
        when(service.findCustomerOrderShipping("proceso")).thenReturn(rows);

        var response = controller.getCustomerShipping(authWithEmail("admin@flashstock.com", false), "proceso");

        assertEquals(rows, response.getBody().getData());
    }

    @Test
    void getCustomerShippingRejectsNonAdmin() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getCustomerShipping(authWithEmail("buyer@flashstock.com", false), null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void getMyHistoryUsesAuthenticatedEmail() {
        List<OrderCustomerShippingResponse> rows = List.of(sampleCustomerShipping());
        when(service.findMyOrderHistory("buyer@flashstock.com", null)).thenReturn(rows);

        var response = controller.getMyHistory(authWithEmail("buyer@flashstock.com", false), null);

        assertEquals(rows, response.getBody().getData());
    }

    @Test
    void confirmReceivedUsesAuthenticatedEmail() {
        OrderResponse order = sampleOrderResponse();
        when(service.confirmReceived("ORD-1", "buyer@flashstock.com")).thenReturn(order);

        var response = controller.confirmReceived(authWithEmail("buyer@flashstock.com", false), "ORD-1");

        assertEquals(order, response.getBody().getData());
    }

    private Authentication authWithRole(String role) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("user@flashstock.com");
        when(authentication.getPrincipal()).thenReturn("user@flashstock.com");
        return authentication;
    }

    private Authentication authWithEmail(String email, boolean adminRole) {
        Authentication authentication = mock(Authentication.class);
        OAuth2User principal = mock(OAuth2User.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        doReturn(adminRole ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn(email);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttributes()).thenReturn(Map.of("email", email));
        return authentication;
    }

    private OrderRequest sampleOrderRequest() {
        OrderRequest request = new OrderRequest();
        request.setInventoryId(1L);
        request.setSku("SKU-1");
        request.setQuantity(2);
        request.setCustomerFirstName("Ana");
        request.setCustomerLastName("Perez");
        request.setCustomerEmail("buyer@flashstock.com");
        request.setShippingAddress("Providencia 123, Santiago");
        return request;
    }

    private OrderResponse sampleOrderResponse() {
        return OrderResponse.builder()
                .orderNumber("ORD-1")
                .inventoryId(1L)
                .sku("SKU-1")
                .quantity(2)
                .customerFirstName("Ana")
                .customerLastName("Perez")
                .customerEmail("buyer@flashstock.com")
                .shippingAddress("Providencia 123, Santiago")
                .status("proceso")
                .build();
    }

    private OrderCustomerShippingResponse sampleCustomerShipping() {
        return OrderCustomerShippingResponse.builder()
                .orderId(1L)
                .orderNumber("ORD-1")
                .orderStatus("proceso")
                .shipmentTrackingNumber("TRK-1")
                .shipmentStatus("proceso")
                .carrier("fast")
                .customerFirstName("Ana")
                .customerLastName("Perez")
                .customerEmail("buyer@flashstock.com")
                .shippingAddress("Providencia 123, Santiago")
                .build();
    }
}