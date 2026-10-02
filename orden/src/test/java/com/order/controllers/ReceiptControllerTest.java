package com.order.controllers;

import com.order.common.ApiResponse;
import com.order.dtos.ReceiptEmailRequest;
import com.order.dtos.ReceiptLineItem;
import com.order.dtos.ReceiptShipmentInfo;
import com.order.services.ReceiptDataService;
import com.order.services.ReceiptEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptControllerTest {

    private ReceiptEmailService receiptEmailService;
    private ReceiptDataService receiptDataService;
    private ReceiptController controller;

    @BeforeEach
    void setUp() {
        receiptEmailService = mock(ReceiptEmailService.class);
        receiptDataService = mock(ReceiptDataService.class);
        controller = new ReceiptController(receiptEmailService, receiptDataService);
    }

    @Test
    void getReceiptFromOrdersBuildsReceiptForAuthenticatedUser() {
        ReceiptEmailRequest receipt = sampleReceipt();
        when(receiptDataService.buildFromOrderNumbers(List.of("ORD-1", "ORD-2"), "buyer@flashstock.com")).thenReturn(receipt);

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("buyer@flashstock.com");
        when(authentication.getPrincipal()).thenReturn("buyer@flashstock.com");

        var response = controller.getReceiptFromOrders("ORD-1, ORD-2", authentication);

        assertEquals(receipt, response.getBody().getData());
        verify(receiptDataService).buildFromOrderNumbers(List.of("ORD-1", "ORD-2"), "buyer@flashstock.com");
    }

    @Test
    void getReceiptFromOrdersRejectsAnonymousUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> controller.getReceiptFromOrders("ORD-1", authentication));
    }

    @Test
    void sendReceiptByEmailDelegatesToService() {
        ReceiptEmailRequest request = sampleReceipt();
        doNothing().when(receiptEmailService).sendReceiptEmail(any(ReceiptEmailRequest.class));

        var response = controller.sendReceiptByEmail(request);

        verify(receiptEmailService).sendReceiptEmail(request);
        assertEquals("Boleta enviada por correo", response.getBody().getMessage());
        assertEquals("RCP-1", response.getBody().getData());
    }

    private ReceiptEmailRequest sampleReceipt() {
        ReceiptEmailRequest request = new ReceiptEmailRequest();
        request.setReceiptNumber("RCP-1");
        request.setCreatedAt("2026-06-09T10:00:00");
        request.setCustomerEmail("buyer@flashstock.com");
        request.setCustomerFirstName("Ana");
        request.setCustomerLastName("Perez");
        request.setShippingAddress("Providencia 123, Santiago");
        request.setSubtotal(new BigDecimal("100.00"));
        request.setShipping(new BigDecimal("5.00"));
        request.setDiscount(new BigDecimal("10.00"));
        request.setTotal(new BigDecimal("95.00"));
        request.setItems(List.of(sampleItem()));
        request.setShipments(List.of(sampleShipment()));
        return request;
    }

    private ReceiptLineItem sampleItem() {
        ReceiptLineItem item = new ReceiptLineItem();
        item.setInventoryId(1L);
        item.setSku("SKU-1");
        item.setProductName("Manzana roja");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setLineTotal(new BigDecimal("100.00"));
        item.setOrderNumber("ORD-1");
        return item;
    }

    private ReceiptShipmentInfo sampleShipment() {
        ReceiptShipmentInfo shipment = new ReceiptShipmentInfo();
        shipment.setOrderNumber("ORD-1");
        shipment.setTrackingNumber("TRK-1");
        shipment.setCarrier("fast");
        shipment.setCourierName("Repartidor fast");
        shipment.setStatus("proceso");
        shipment.setEta("48h");
        return shipment;
    }
}