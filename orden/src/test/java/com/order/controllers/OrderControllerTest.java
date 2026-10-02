
package com.order.controllers;

import com.order.dtos.OrderCustomerShippingResponse;
import com.order.dtos.OrderRequest;
import com.order.dtos.OrderResponse;
import com.order.services.OrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private OrderService service;
    private OrderController controller;

    // =========================================================
    // CONFIGURACIÓN
    // =========================================================

    @BeforeEach
    void setUp() {

        service = mock(OrderService.class);

        controller = new OrderController(service);

        ReflectionTestUtils.setField(
                controller,
                "adminEmail",
                "aron83353@gmail.com"
        );
        ReflectionTestUtils.setField(controller, "legacyAdminEmailEnabled", false);
    }

    // =========================================================
    // TEST: OBTENER TODOS LOS PEDIDOS
    // =========================================================

    @Test
    void getAllReturnsOrders() {

        List<OrderResponse> orders =
                List.of(sampleOrderResponse());

        when(service.findAll())
                .thenReturn(orders);

        var response = controller.getAll();

        assertEquals(
                orders,
                response.getBody().getData()
        );
    }

    // =========================================================
    // TEST: CREAR PEDIDO
    // =========================================================

    @Test
    void createUsesAuthenticatedEmailWhenCustomerEmailIsBlank() {

        OrderRequest request = sampleOrderRequest();

        request.setCustomerEmail(" ");

        Authentication authentication =
                authWithEmail(
                        "buyer@flashstock.com",
                        false
                );

        OrderResponse order = sampleOrderResponse();

        when(service.create(any(OrderRequest.class)))
                .thenReturn(order);

        var response = controller.create(
                request,
                authentication
        );

        assertEquals(
                "Pedido creado",
                response.getBody().getMessage()
        );

        assertEquals(
                order,
                response.getBody().getData()
        );

        verify(service).create(request);

        assertEquals(
                "buyer@flashstock.com",
                request.getCustomerEmail()
        );
    }

    // =========================================================
    // TEST: BUSCAR POR NÚMERO DE PEDIDO
    // =========================================================

    @Test
    void getByOrderNumberReturnsOrder() {

        OrderResponse order = sampleOrderResponse();

        when(service.findByOrderNumber("ORD-1"))
                .thenReturn(order);

        var response =
                controller.getByOrderNumber("ORD-1");

        assertEquals(
                order,
                response.getBody().getData()
        );
    }

    // =========================================================
    // TEST: ACTUALIZAR ESTADO
    // =========================================================

    @Test
    void updateStatusDelegatesToService() {

        OrderResponse order = sampleOrderResponse();

        when(service.updateStatus("ORD-1", "enviado"))
                .thenReturn(order);

        var response = controller.updateStatus(
                "ORD-1",
                "enviado"
        );

        assertEquals(
                order,
                response.getBody().getData()
        );
    }

    // =========================================================
    // TEST: ADMINISTRADOR POR ROL
    // =========================================================

    @Test
    void getCustomerShippingAllowsAdminByRole() {

        List<OrderCustomerShippingResponse> rows =
                List.of(sampleCustomerShipping());

        when(service.findCustomerOrderShipping(null))
                .thenReturn(rows);

        Authentication authentication =
                authWithRole("ROLE_ADMIN");

        var response = controller.getCustomerShipping(
                authentication,
                null
        );

        assertEquals(
                rows,
                response.getBody().getData()
        );

        verify(service).findCustomerOrderShipping(null);
    }

    // =========================================================
    // TEST: EMAIL CONFIGURADO SIN ROLE_ADMIN DEBE SER RECHAZADO
    // =========================================================

    @Test
    void getCustomerShippingRejectsConfiguredEmailWithoutAdminRole() {
        Authentication authentication = authWithEmail("aron83353@gmail.com", false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getCustomerShipping(authentication, "proceso"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    // =========================================================
    // TEST: RECHAZAR USUARIO NO ADMINISTRADOR
    // =========================================================

    @Test
    void getCustomerShippingRejectsNonAdmin() {

        Authentication authentication =
                authWithEmail(
                        "buyer@flashstock.com",
                        false
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> controller.getCustomerShipping(
                                authentication,
                                null
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(service);
    }

    // =========================================================
    // TEST: RECHAZAR ROLE_USER
    // =========================================================

    @Test
    void getCustomerShippingRejectsUserRole() {

        Authentication authentication =
                authWithRole("ROLE_USER");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> controller.getCustomerShipping(
                                authentication,
                                null
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        // El servicio no debe ejecutarse si no hay autorización.
        verifyNoInteractions(service);
    }

    // =========================================================
    // TEST: HISTORIAL DEL USUARIO AUTENTICADO
    // =========================================================

    @Test
    void getMyHistoryUsesAuthenticatedEmail() {

        List<OrderCustomerShippingResponse> rows =
                List.of(sampleCustomerShipping());

        when(
                service.findMyOrderHistory(
                        "buyer@flashstock.com",
                        null
                )
        ).thenReturn(rows);

        Authentication authentication =
                authWithEmail(
                        "buyer@flashstock.com",
                        false
                );

        var response = controller.getMyHistory(
                authentication,
                null
        );

        assertEquals(
                rows,
                response.getBody().getData()
        );
    }

    // =========================================================
    // TEST: CONFIRMAR RECEPCIÓN
    // =========================================================

    @Test
    void confirmReceivedUsesAuthenticatedEmail() {

        OrderResponse order = sampleOrderResponse();

        when(
                service.confirmReceived(
                        "ORD-1",
                        "buyer@flashstock.com"
                )
        ).thenReturn(order);

        Authentication authentication =
                authWithEmail(
                        "buyer@flashstock.com",
                        false
                );

        var response = controller.confirmReceived(
                authentication,
                "ORD-1"
        );

        assertEquals(
                order,
                response.getBody().getData()
        );
    }

    // =========================================================
    // MOCK: AUTENTICACIÓN MEDIANTE ROL
    // =========================================================

    private Authentication authWithRole(String role) {

        Authentication authentication =
                mock(Authentication.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        // CORRECCIÓN:
        // Utilizamos el parámetro recibido, no ROLE_USER fijo.
        doReturn(
                List.of(new SimpleGrantedAuthority(role))
        )
                .when(authentication)
                .getAuthorities();

        when(authentication.getName())
                .thenReturn("user@flashstock.com");

        when(authentication.getPrincipal())
                .thenReturn("user@flashstock.com");

        return authentication;
    }

    // =========================================================
    // MOCK: AUTENTICACIÓN MEDIANTE EMAIL / OAUTH2
    // =========================================================

    private Authentication authWithEmail(
            String email,
            boolean adminRole
    ) {

        Authentication authentication =
                mock(Authentication.class);

        OAuth2User principal =
                mock(OAuth2User.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        doReturn(
                adminRole
                        ? List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_ADMIN"
                                )
                        )
                        : List.of()
        )
                .when(authentication)
                .getAuthorities();

        when(authentication.getName())
                .thenReturn(email);

        when(authentication.getPrincipal())
                .thenReturn(principal);

        when(principal.getAttributes())
                .thenReturn(
                        Map.of("email", email)
                );

        return authentication;
    }

    // =========================================================
    // DATOS DE PRUEBA: ORDER REQUEST
    // =========================================================

    private OrderRequest sampleOrderRequest() {

        OrderRequest request = new OrderRequest();

        request.setInventoryId(1L);
        request.setSku("SKU-1");
        request.setQuantity(2);

        request.setCustomerFirstName("Ana");
        request.setCustomerLastName("Perez");
        request.setCustomerEmail("buyer@flashstock.com");

        request.setShippingAddress(
                "Providencia 123, Santiago"
        );

        return request;
    }

    // =========================================================
    // DATOS DE PRUEBA: ORDER RESPONSE
    // =========================================================

    private OrderResponse sampleOrderResponse() {

        return OrderResponse.builder()
                .orderNumber("ORD-1")
                .inventoryId(1L)
                .sku("SKU-1")
                .quantity(2)
                .customerFirstName("Ana")
                .customerLastName("Perez")
                .customerEmail("buyer@flashstock.com")
                .shippingAddress(
                        "Providencia 123, Santiago"
                )
                .status("proceso")
                .build();
    }

    // =========================================================
    // DATOS DE PRUEBA: CUSTOMER SHIPPING
    // =========================================================

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
                .shippingAddress(
                        "Providencia 123, Santiago"
                )
                .build();
    }
}
