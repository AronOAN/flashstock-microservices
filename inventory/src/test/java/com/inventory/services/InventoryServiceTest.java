package com.inventory.services;

import com.inventory.daos.CartDao;
import com.inventory.daos.InventoryDao;
import com.inventory.daos.OrderDao;
import com.inventory.daos.ShipmentDao;
import com.inventory.dtos.InventoryRealtimeResponse;
import com.inventory.dtos.InventoryRequest;
import com.inventory.dtos.InventoryResponse;
import com.inventory.models.CustomerOrder;
import com.inventory.models.Inventory;
import com.inventory.models.Shipment;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    private final CartDao carts = mock(CartDao.class);
    private final InventoryDao inventory = mock(InventoryDao.class);
    private final OrderDao orders = mock(OrderDao.class);
    private final ShipmentDao shipments = mock(ShipmentDao.class);
    private final InventoryService service = new InventoryService(carts, inventory, orders, shipments);

    @Test
    void hidesInactiveProductsAndSubtractsReservationsFromAvailableStock() {
        when(inventory.findAll()).thenReturn(List.of(product("SKU-1", 6, true), product("HIDDEN", 100, false)));
        when(carts.reservedUnitsBySku()).thenReturn(Map.of("SKU-1", 4));

        List<InventoryResponse> result = service.findAll();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getQuantity());
        assertEquals(6, result.get(0).getStock());
    }

    @Test
    void realtimeStatusCountsShipmentsBySkuAndRisk() {
        when(inventory.findAll()).thenReturn(List.of(product("SKU-1", 6, true)));
        when(carts.reservedUnitsBySku()).thenReturn(Map.of("SKU-1", 4));
        when(orders.findAll()).thenReturn(List.of(
                CustomerOrder.builder().orderNumber("O1").sku("SKU-1").quantity(2).build(),
                CustomerOrder.builder().orderNumber("O2").sku("SKU-1").quantity(1).build(),
                CustomerOrder.builder().orderNumber("O3").sku("SKU-1").quantity(1).build()));
        when(shipments.findAll()).thenReturn(List.of(
                Shipment.builder().orderNumber("O1").status("delivered").build(),
                Shipment.builder().orderNumber("O2").status("in_transit").build(),
                Shipment.builder().orderNumber("O3").status("preparing").build(),
                Shipment.builder().orderNumber("OTHER").status("delivered").build()));

        InventoryRealtimeResponse row = service.findRealtimeStatus().get(0);
        assertEquals(4, row.getOrderedUnits());
        assertEquals(2, row.getAvailableToSell());
        assertEquals("LOW", row.getRiskLevel());
        assertEquals(1, row.getPreparingShipments());
        assertEquals(1, row.getInTransitShipments());
        assertEquals(1, row.getDeliveredShipments());
    }

    @Test
    void createsProductWithNormalizedSkuAndDefaults() {
        InventoryRequest request = new InventoryRequest();
        request.setSku(" SKU-2 ");
        request.setName("Pera");
        request.setWarehouse("B");
        request.setUnitPrice(new BigDecimal("4.50"));
        request.setQuantity(8);
        when(inventory.findBySku("SKU-2")).thenReturn(Optional.empty());
        when(inventory.save(any(Inventory.class))).thenAnswer(call -> call.getArgument(0));

        InventoryResponse created = service.create(request);
        assertEquals("SKU-2", created.getSku());
        assertEquals(8, created.getStock());
        assertEquals(new BigDecimal("4.50"), created.getPrice());
        assertTrue(created.getActive());
    }

    @Test
    void rejectsDuplicateSkuBeforeWritingAndDeletesCartReferencesFirst() {
        InventoryRequest request = new InventoryRequest();
        request.setSku("SKU-1");
        when(inventory.findBySku("SKU-1")).thenReturn(Optional.of(product("SKU-1", 1, true)));
        assertThrows(IllegalStateException.class, () -> service.create(request));
        verify(inventory, never()).save(any());

        service.deleteBySku("SKU-1");
        var calls = inOrder(carts, inventory);
        calls.verify(carts).deleteBySku("SKU-1");
        calls.verify(inventory).deleteBySku("SKU-1");
    }

    @Test
    void readsStockAfterReservationsAndUpdatesTheExistingSku() {
        Inventory current = product("SKU-1", 6, true);
        when(inventory.findBySku("SKU-1")).thenReturn(Optional.of(current));
        when(carts.reservedUnitsBySku()).thenReturn(Map.of("SKU-1", 2));
        when(inventory.save(current)).thenReturn(current);

        assertEquals(4, service.findBySku("SKU-1").getQuantity());
        assertEquals(9, service.updateQuantity("SKU-1", 9).getStock());
        InventoryRequest replacement = new InventoryRequest();
        replacement.setName("Nuevo nombre");
        replacement.setPrice(new BigDecimal("19.90"));
        replacement.setStock(5);
        replacement.setActive(false);
        InventoryResponse updated = service.updateProduct("SKU-1", replacement);
        assertEquals(5, updated.getStock());
        assertEquals(new BigDecimal("19.90"), updated.getUnitPrice());
        assertEquals(false, updated.getActive());
        assertEquals("Nuevo nombre", updated.getName());
    }

    @Test
    void rejectsMissingSkuAndDatabaseConstraintsWithoutSilentlyOverwritingProduct() {
        InventoryRequest request = new InventoryRequest();
        request.setSku(" ");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setSku("SKU-1");
        request.setUnitPrice(BigDecimal.ONE);
        when(inventory.findBySku("SKU-1")).thenReturn(Optional.empty());
        when(inventory.save(any(Inventory.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        assertThrows(IllegalStateException.class, () -> service.create(request));
        assertThrows(IllegalArgumentException.class, () -> service.findBySku("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteBySku("UNKNOWN"));
    }

    @Test
    void deletionReportsActiveForeignKeyReferences() {
        when(inventory.findBySku("SKU-1")).thenReturn(Optional.of(product("SKU-1", 6, true)));
        doThrow(new DataIntegrityViolationException("foreign key"))
                .when(inventory).deleteBySku("SKU-1");
        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> service.deleteBySku("SKU-1"));
        assertTrue(conflict.getMessage().contains("referencias activas"));
    }

    private Inventory product(String sku, int quantity, boolean active) {
        return Inventory.builder().sku(sku).quantity(quantity).name("Producto")
                .warehouse("A").unitPrice(BigDecimal.ONE).active(active).build();
    }
}
