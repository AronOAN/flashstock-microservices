package com.order.services;

import com.order.daos.InventoryDao;
import com.order.daos.OrderDao;
import com.order.daos.ShipmentDao;
import com.order.dtos.OrderRequest;
import com.order.dtos.OrderResponse;
import com.order.models.CustomerOrder;
import com.order.models.Inventory;
import com.order.models.Shipment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceWorkflowTest {
    private final OrderDao orders = mock(OrderDao.class);
    private final InventoryDao inventory = mock(InventoryDao.class);
    private final ShipmentDao shipments = mock(ShipmentDao.class);
    private final OrderService service = new OrderService(orders, inventory, shipments);

    @Test
    void createsOrderWithVerifiedOwnerAndAtomicStockDeduction() {
        Inventory product = Inventory.builder().id(12L).sku("SKU").quantity(5).active(true).build();
        OrderRequest input = request(2);
        input.setCustomerEmail("attacker@example.com");
        when(inventory.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        when(inventory.save(product)).thenReturn(product);
        when(orders.save(any(CustomerOrder.class))).thenAnswer(call -> call.getArgument(0));

        OrderResponse response = service.create(input, "verified-sub", "owner@example.com");

        assertEquals(3, product.getQuantity());
        assertEquals("owner@example.com", response.getCustomerEmail());
        assertEquals("proceso", response.getStatus());
        assertTrue(response.getOrderNumber().startsWith("ORD-"));
        verify(orders).save(argThat(order -> "verified-sub".equals(order.getCustomerSub())
                && "owner@example.com".equals(order.getCustomerEmail())
                && order.getCreatedAt() != null));
    }

    @Test
    void rejectsAnonymousAndInsufficientStockWithoutPersisting() {
        OrderRequest input = request(10);
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> service.create(input, " ", "owner@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        verifyNoInteractions(inventory, orders);

        Inventory product = Inventory.builder().id(12L).sku("SKU").quantity(2).active(true).build();
        when(inventory.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        assertThrows(IllegalStateException.class,
                () -> service.create(input, "verified-sub", "owner@example.com"));
        assertEquals(2, product.getQuantity());
        verify(inventory, never()).save(any());
        verifyNoInteractions(orders);
    }

    @Test
    void ownerCanConfirmShippedOrderAndShipmentTogether() {
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").customerSub("owner")
                .status("enviado").sku("SKU").quantity(1).build();
        Shipment shipment = Shipment.builder().orderNumber("ORD-1").status("enviado").build();
        when(orders.lockOwnedOrder("ORD-1", "owner")).thenReturn(Optional.of(order));
        when(shipments.findByOrderNumber("ORD-1")).thenReturn(Optional.of(shipment));

        OrderResponse response = service.confirmReceived("ORD-1", "owner");

        assertEquals("completado", response.getStatus());
        assertEquals("completado", shipment.getStatus());
        assertNotNull(shipment.getLastUpdate());
        verify(orders).save(order);
        verify(shipments).save(shipment);
    }

    @Test
    void regularUserLookupNeverFallsBackToUnrestrictedDao() {
        when(orders.findByOrderNumberAndCustomerSub("ORD-1", "owner"))
                .thenReturn(Optional.of(CustomerOrder.builder().orderNumber("ORD-1")
                        .customerSub("owner").status("proceso").build()));
        assertEquals("ORD-1", service.findByOrderNumber("ORD-1", "owner", false).getOrderNumber());
        verify(orders, never()).findByOrderNumber(anyString());
    }

    @Test
    void rejectsInactiveProductsAndNonPositiveQuantities() {
        OrderRequest input = request(1);
        Inventory inactive = Inventory.builder().id(12L).sku("SKU").quantity(5).active(false).build();
        when(inventory.findByIdForUpdate(12L)).thenReturn(Optional.of(inactive));
        assertThrows(IllegalStateException.class,
                () -> service.create(input, "owner", "owner@example.test"));
        inactive.setActive(true);
        input.setQuantity(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.create(input, "owner", "owner@example.test"));
        verify(inventory, never()).save(any());
        verifyNoInteractions(orders);
    }

    @Test
    void invalidOrderStatusCannotOverwritePersistedState() {
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").status("proceso").build();
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus("ORD-1", "invalid"));
        assertEquals("proceso", order.getStatus());
        verify(orders, never()).save(any());
        when(orders.save(order)).thenReturn(order);
        assertEquals("enviado", service.updateStatus("ORD-1", " ENVIADO ").getStatus());
    }

    @Test
    void confirmedOrderWithoutShipmentIsStillOwnedAndComplete() {
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").customerSub("owner")
                .status("enviado").build();
        when(orders.lockOwnedOrder("ORD-1", "owner")).thenReturn(Optional.of(order));
        assertEquals("completado", service.confirmReceived("ORD-1", "owner").getStatus());
        verify(shipments, never()).save(any());
    }

    @Test
    void duplicateOrderNumberRetriesWithoutDoubleDecrementingStock() {
        OrderRequest input = request(1);
        Inventory product = Inventory.builder().id(12L).sku("SKU").quantity(5).active(true).build();
        when(inventory.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        when(orders.save(any(CustomerOrder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(call -> call.getArgument(0));
        assertEquals("proceso", service.create(input, "owner", null).getStatus());
        assertEquals(4, product.getQuantity());
        verify(inventory, times(1)).save(product);
        verify(orders, times(2)).save(any(CustomerOrder.class));
    }

    private OrderRequest request(int quantity) {
        OrderRequest request = new OrderRequest();
        request.setInventoryId(12L);
        request.setSku("SKU");
        request.setQuantity(quantity);
        return request;
    }
}
