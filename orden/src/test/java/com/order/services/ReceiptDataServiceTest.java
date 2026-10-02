package com.order.services;

import com.order.daos.InventoryDao;
import com.order.daos.OrderDao;
import com.order.daos.ShipmentDao;
import com.order.dtos.ReceiptEmailRequest;
import com.order.models.CustomerOrder;
import com.order.models.Inventory;
import com.order.models.Shipment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiptDataServiceTest {
    private final OrderDao orders = mock(OrderDao.class);
    private final InventoryDao inventory = mock(InventoryDao.class);
    private final ShipmentDao shipments = mock(ShipmentDao.class);
    private final ReceiptDataService service = new ReceiptDataService(orders, inventory, shipments);

    @Test
    void buildsOnlyOwnedOrdersWithCurrentPricesAndShipmentFallbacks() {
        CustomerOrder first = CustomerOrder.builder().orderNumber("ORD-1").customerSub("owner")
                .customerEmail("owner@example.com").customerFirstName("Ana").shippingAddress("Santiago")
                .inventoryId(7L).sku("SKU-1").quantity(2)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
        CustomerOrder second = CustomerOrder.builder().orderNumber("ORD-2").customerSub("owner")
                .sku("SKU-2").quantity(1).createdAt(LocalDateTime.of(2026, 9, 2, 10, 0)).build();
        when(orders.findByOrderNumberAndCustomerSub("ORD-1", "owner")).thenReturn(Optional.of(first));
        when(orders.findByOrderNumberAndCustomerSub("ORD-2", "owner")).thenReturn(Optional.of(second));
        when(inventory.findById(7L)).thenReturn(Optional.of(
                Inventory.builder().id(7L).sku("SKU-1").name("Manzana").unitPrice(new BigDecimal("10.00")).build()));
        when(inventory.findBySku("SKU-2")).thenReturn(Optional.of(
                Inventory.builder().sku("SKU-2").name("Pera").unitPrice(new BigDecimal("4.00")).build()));
        when(shipments.findByOrderNumber("ORD-1")).thenReturn(Optional.of(
                Shipment.builder().orderNumber("ORD-1").trackingNumber("TRK-1")
                        .carrier("DHL").status("enviado").build()));
        when(shipments.findByOrderNumber("ORD-2")).thenReturn(Optional.empty());

        ReceiptEmailRequest receipt = service.buildFromOrderNumbers(List.of(" ORD-1 ", "ORD-2"), "owner");
        assertEquals("BOL-ORD-1", receipt.getReceiptNumber());
        assertEquals("owner@example.com", receipt.getCustomerEmail());
        assertEquals("2026-09-02T10:00:00", receipt.getCreatedAt());
        assertEquals(new BigDecimal("24.00"), receipt.getSubtotal());
        assertEquals(new BigDecimal("27.00"), receipt.getTotal());
        assertEquals("Manzana", receipt.getItems().get(0).getProductName());
        assertEquals("Repartidor DHL", receipt.getShipments().get(0).getCourierName());
        assertEquals("Repartidor FlashStock", receipt.getShipments().get(1).getCourierName());
        verify(orders, never()).findByOrderNumber(anyString());
    }

    @Test
    void rejectsDuplicateNumbersBeforeReadingAnyOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buildFromOrderNumbers(List.of(" ORD-1", "ORD-1 "), "owner"));
        verifyNoInteractions(orders, inventory, shipments);
    }

    @Test
    void missingProductPriceDoesNotInventCharges() {
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").customerSub("owner")
                .sku("UNKNOWN").quantity(1).build();
        when(orders.findByOrderNumberAndCustomerSub("ORD-1", "owner")).thenReturn(Optional.of(order));
        ReceiptEmailRequest receipt = service.buildFromOrderNumbers(List.of("ORD-1"), "owner");
        assertEquals(BigDecimal.ZERO, receipt.getSubtotal());
        assertEquals(new BigDecimal("3"), receipt.getTotal());
    }

    @Test
    void rejectsMissingOwnerBlankNumbersAndExcessiveBatchBeforeDatabaseLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buildFromOrderNumbers(List.of("ORD-1"), " "));
        assertThrows(IllegalArgumentException.class,
                () -> service.buildFromOrderNumbers(List.of(), "owner"));
        assertThrows(IllegalArgumentException.class,
                () -> service.buildFromOrderNumbers(List.of("ORD-1", "  "), "owner"));
        assertThrows(IllegalArgumentException.class,
                () -> service.buildFromOrderNumbers(java.util.Collections.nCopies(21, "ORD-1"), "owner"));
        verifyNoInteractions(orders, inventory, shipments);
    }

    @Test
    void unknownInventoryNeverCreatesPriceAndExistingCourierNameIsPreserved() {
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").customerSub("owner")
                .sku("SKU").quantity(null).build();
        when(orders.findByOrderNumberAndCustomerSub("ORD-1", "owner")).thenReturn(Optional.of(order));
        when(inventory.findBySku("SKU")).thenReturn(Optional.of(
                Inventory.builder().sku("SKU").name("Producto").build()));
        when(shipments.findByOrderNumber("ORD-1")).thenReturn(Optional.of(
                Shipment.builder().orderNumber("ORD-1").carrier("DHL")
                        .courierName("Ana").status("enviado").build()));

        ReceiptEmailRequest receipt = service.buildFromOrderNumbers(List.of("ORD-1"), "owner");

        assertEquals(BigDecimal.ZERO, receipt.getItems().get(0).getLineTotal());
        assertEquals("Ana", receipt.getShipments().get(0).getCourierName());
        assertNotNull(receipt.getCreatedAt());
    }
}
