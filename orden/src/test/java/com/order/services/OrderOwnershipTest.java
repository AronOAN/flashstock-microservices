package com.order.services;

import com.order.daos.InventoryDao;
import com.order.daos.OrderDao;
import com.order.daos.ShipmentDao;
import com.order.models.CustomerOrder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderOwnershipTest {
    private final OrderDao orders = mock(OrderDao.class);
    private final InventoryDao inventory = mock(InventoryDao.class);
    private final ShipmentDao shipments = mock(ShipmentDao.class);
    private final OrderService service = new OrderService(orders, inventory, shipments);

    @Test
    void crossAccountAndLegacyOrderReturnSameNotFound() {
        when(orders.findByOrderNumberAndCustomerSub("ORD-1", "other-sub")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.findByOrderNumber("ORD-1", "other-sub", false)).getStatusCode());
        verify(orders).findByOrderNumberAndCustomerSub("ORD-1", "other-sub");
        verify(orders, never()).findByOrderNumber("ORD-1");
    }

    @Test
    void crossAccountCannotChangeOrderOrShipment() {
        when(orders.lockOwnedOrder("ORD-1", "other-sub")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.confirmReceived("ORD-1", "other-sub")).getStatusCode());
        verify(orders, never()).save(any(CustomerOrder.class));
        verifyNoInteractions(shipments);
    }

    @Test
    void ownedOrderMustBeShippedBeforeConfirmation() {
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").customerSub("owner-sub")
                .status("cancelado").build();
        when(orders.lockOwnedOrder("ORD-1", "owner-sub")).thenReturn(Optional.of(order));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.confirmReceived("ORD-1", "owner-sub")).getStatusCode());
        verify(orders, never()).save(any(CustomerOrder.class));
        verifyNoInteractions(shipments);
    }

    @Test
    void historyIsFilteredBySubject() {
        when(orders.findCustomerOrderShippingBySub("owner-sub", null)).thenReturn(List.of());
        assertEquals(List.of(), service.findMyOrderHistory("owner-sub", null));
        verify(orders).findCustomerOrderShippingBySub("owner-sub", null);
    }

    @Test
    void receiptRequiresEveryOrderToBelongToSubject() {
        ReceiptDataService receipts = new ReceiptDataService(orders, inventory, shipments);
        when(orders.findByOrderNumberAndCustomerSub("ORD-1", "owner-sub"))
                .thenReturn(Optional.of(CustomerOrder.builder().orderNumber("ORD-1").build()));
        when(orders.findByOrderNumberAndCustomerSub("ORD-2", "owner-sub"))
                .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> receipts.buildFromOrderNumbers(List.of("ORD-1", "ORD-2"), "owner-sub"));
        verify(orders, never()).findByOrderNumber(anyString());
    }
}
