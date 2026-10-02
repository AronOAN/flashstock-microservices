package com.auth.services.admin;

import com.auth.daos.CartDao;
import com.auth.daos.InventoryDao;
import com.auth.daos.OrderDao;
import com.auth.daos.ShipmentDao;
import com.auth.dtos.AdminMetricsResponse;
import com.auth.models.CustomerOrder;
import com.auth.models.Inventory;
import com.auth.models.Shipment;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMetricsServiceTest {

    @Test
    void aggregatesInventoryOrdersShipmentsAndRealizedCashflow() {
        CartDao carts = mock(CartDao.class);
        InventoryDao inventory = mock(InventoryDao.class);
        OrderDao orders = mock(OrderDao.class);
        ShipmentDao shipments = mock(ShipmentDao.class);
        AdminMetricsService service = new AdminMetricsService(carts, inventory, orders, shipments);
        ReflectionTestUtils.setField(service, "unitValue", 12.5);

        when(inventory.findAll()).thenReturn(List.of(
                Inventory.builder().sku("LOW").warehouse("A").quantity(7).active(true).build(),
                Inventory.builder().sku("CRITICAL").warehouse("A").quantity(1).active(true).build(),
                Inventory.builder().sku("DISABLED").quantity(50).active(false).build()));
        when(carts.reservedUnitsBySku()).thenReturn(Map.of("LOW", 4, "CRITICAL", 2));
        when(orders.findAll()).thenReturn(List.of(
                CustomerOrder.builder().orderNumber("O1").sku("LOW").quantity(2).status("proceso").build(),
                CustomerOrder.builder().orderNumber("O2").sku("LOW").quantity(3).status("cancelado").build(),
                CustomerOrder.builder().orderNumber("O3").sku("CRITICAL").quantity(1).status("completado").build()));
        when(shipments.findAll()).thenReturn(List.of(
                Shipment.builder().orderNumber("O1").status("en_transit").build(),
                Shipment.builder().orderNumber("O2").status("preparing").build(),
                Shipment.builder().orderNumber("O3").status("delivered").build()));

        AdminMetricsResponse response = service.getAdminMetrics();
        assertEquals(2, response.getInventorySkuCount());
        assertEquals(8, response.getTotalStock());
        assertEquals(1, response.getLowRiskSkuCount());
        assertEquals(1, response.getCriticalRiskSkuCount());
        assertEquals(1, response.getCreatedOrders());
        assertEquals(1, response.getCancelledOrders());
        assertEquals(1, response.getCompletedOrders());
        assertEquals(1, response.getPreparingShipments());
        assertEquals(1, response.getInTransitShipments());
        assertEquals(1, response.getDeliveredShipments());
        assertEquals(75.0, response.getGrossCashflow());
        assertEquals(12.5, response.getRealizedCashflow());
        assertEquals(62.5, response.getPendingCashflow());
        assertEquals(3, response.getSkuMetrics().get(0).getAvailableUnits());
        assertEquals(0, response.getSkuMetrics().get(1).getAvailableUnits());
    }

    @Test
    void emptyStoresReturnZeroMetrics() {
        CartDao carts = mock(CartDao.class);
        InventoryDao inventory = mock(InventoryDao.class);
        OrderDao orders = mock(OrderDao.class);
        ShipmentDao shipments = mock(ShipmentDao.class);
        when(carts.reservedUnitsBySku()).thenReturn(Map.of());
        when(inventory.findAll()).thenReturn(List.of());
        when(orders.findAll()).thenReturn(List.of());
        when(shipments.findAll()).thenReturn(List.of());
        AdminMetricsService service = new AdminMetricsService(carts, inventory, orders, shipments);

        AdminMetricsResponse response = service.getAdminMetrics();
        assertEquals(0, response.getTotalOrders());
        assertEquals(0, response.getTotalShipments());
        assertEquals(0.0, response.getPendingCashflow());
    }
}
