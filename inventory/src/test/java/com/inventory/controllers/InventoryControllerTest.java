package test.java.com.inventory.controllers;

import com.inventory.common.ApiResponse;
import com.inventory.dtos.InventoryRealtimeResponse;
import com.inventory.dtos.InventoryRequest;
import com.inventory.dtos.InventoryResponse;
import com.inventory.services.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryControllerTest {

    private InventoryService service;
    private InventoryController controller;

    @BeforeEach
    void setUp() {
        service = mock(InventoryService.class);
        controller = new InventoryController(service);
    }

    @Test
    void getAllReturnsInventoryList() {
        List<InventoryResponse> items = List.of(sampleInventoryResponse());
        when(service.findAll()).thenReturn(items);

        ResponseEntity<ApiResponse<List<InventoryResponse>>> response = controller.getAll();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Inventario listado", response.getBody().getMessage());
        assertEquals(items, response.getBody().getData());
    }

    @Test
    void getRealtimeReturnsRealtimeStatus() {
        List<InventoryRealtimeResponse> items = List.of(InventoryRealtimeResponse.builder()
                .sku("SKU-1")
                .warehouse("WH-1")
                .currentStock(10)
                .orderedUnits(2)
                .preparingShipments(1)
                .inTransitShipments(0)
                .deliveredShipments(3)
                .availableToSell(8)
                .riskLevel("HEALTHY")
                .build());
        when(service.findRealtimeStatus()).thenReturn(items);

        ResponseEntity<ApiResponse<List<InventoryRealtimeResponse>>> response = controller.getRealtime();

        assertEquals(items, response.getBody().getData());
    }

    @Test
    void createDelegatesToService() {
        InventoryRequest request = sampleInventoryRequest();
        InventoryResponse inventory = sampleInventoryResponse();
        when(service.create(any(InventoryRequest.class))).thenReturn(inventory);

        ResponseEntity<ApiResponse<InventoryResponse>> response = controller.create(request);

        assertEquals("Inventario creado", response.getBody().getMessage());
        assertEquals(inventory, response.getBody().getData());
    }

    @Test
    void getBySkuDelegatesToService() {
        InventoryResponse inventory = sampleInventoryResponse();
        when(service.findBySku("SKU-1")).thenReturn(inventory);

        ResponseEntity<ApiResponse<InventoryResponse>> response = controller.getBySku("SKU-1");

        assertEquals(inventory, response.getBody().getData());
    }

    @Test
    void updateQuantityDelegatesToService() {
        InventoryResponse inventory = sampleInventoryResponse();
        when(service.updateQuantity("SKU-1", 15)).thenReturn(inventory);

        ResponseEntity<ApiResponse<InventoryResponse>> response = controller.updateQuantity("SKU-1", 15);

        assertEquals(inventory, response.getBody().getData());
    }

    @Test
    void updateProductDelegatesToService() {
        InventoryRequest request = sampleInventoryRequest();
        InventoryResponse inventory = sampleInventoryResponse();
        when(service.updateProduct("SKU-1", request)).thenReturn(inventory);

        ResponseEntity<ApiResponse<InventoryResponse>> response = controller.updateProduct("SKU-1", request);

        assertEquals(inventory, response.getBody().getData());
    }

    @Test
    void deleteBySkuReturnsSkuInResponse() {
        ResponseEntity<ApiResponse<String>> response = controller.deleteBySku("SKU-1");

        verify(service).deleteBySku("SKU-1");
        assertEquals("Producto eliminado", response.getBody().getMessage());
        assertEquals("SKU-1", response.getBody().getData());
    }

    private InventoryRequest sampleInventoryRequest() {
        InventoryRequest request = new InventoryRequest();
        request.setSku("SKU-1");
        request.setSupplierId(10L);
        request.setName("Manzana roja");
        request.setDescription("Caja de manzanas");
        request.setUnitPrice(new BigDecimal("2.50"));
        request.setPrice(new BigDecimal("2.50"));
        request.setImageUrl("https://example.com/apple.png");
        request.setCategory("fruta");
        request.setQuantity(12);
        request.setStock(12);
        request.setWarehouse("WH-1");
        request.setActive(true);
        return request;
    }

    private InventoryResponse sampleInventoryResponse() {
        return InventoryResponse.builder()
                .id(1L)
                .sku("SKU-1")
                .supplierId(10L)
                .name("Manzana roja")
                .description("Caja de manzanas")
                .unitPrice(new BigDecimal("2.50"))
                .price(new BigDecimal("2.50"))
                .imageUrl("https://example.com/apple.png")
                .category("fruta")
                .quantity(12)
                .stock(12)
                .warehouse("WH-1")
                .active(true)
                .build();
    }
}