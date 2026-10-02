package test.java.com.shipping.controllers;

import com.shipping.common.ApiResponse;
import com.shipping.dtos.ShipmentLiveUpdateRequest;
import com.shipping.dtos.ShipmentRequest;
import com.shipping.dtos.ShipmentResponse;
import com.shipping.dtos.ShipmentRouteStepEtaResponse;
import com.shipping.dtos.ShipmentTrackingResponse;
import com.shipping.services.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingControllerTest {

    private ShippingService service;
    private ShippingController controller;

    @BeforeEach
    void setUp() {
        service = mock(ShippingService.class);
        controller = new ShippingController(service);
    }

    @Test
    void getAllReturnsShipments() {
        List<ShipmentResponse> shipments = List.of(sampleShipmentResponse());
        when(service.findAll()).thenReturn(shipments);

        var response = controller.getAll();

        assertEquals(shipments, response.getBody().getData());
    }

    @Test
    void createDelegatesToService() {
        ShipmentRequest request = new ShipmentRequest();
        request.setOrderNumber("ORD-1");
        request.setCarrier("fast");
        ShipmentResponse shipment = sampleShipmentResponse();
        when(service.create(any(ShipmentRequest.class))).thenReturn(shipment);

        var response = controller.create(request);

        assertEquals(shipment, response.getBody().getData());
    }

    @Test
    void getByTrackingDelegatesToService() {
        ShipmentResponse shipment = sampleShipmentResponse();
        when(service.findByTrackingNumber("TRK-1")).thenReturn(shipment);

        var response = controller.getByTracking("TRK-1");

        assertEquals(shipment, response.getBody().getData());
    }

    @Test
    void getTrackingDelegatesToService() {
        ShipmentTrackingResponse tracking = sampleTrackingResponse();
        when(service.getTrackingSnapshot("TRK-1")).thenReturn(tracking);

        var response = controller.getTracking("TRK-1");

        assertEquals(tracking, response.getBody().getData());
    }

    @Test
    void updateTrackingLiveDelegatesToService() {
        ShipmentTrackingResponse tracking = sampleTrackingResponse();
        ShipmentLiveUpdateRequest request = new ShipmentLiveUpdateRequest();
        when(service.updateTrackingLive("TRK-1", request)).thenReturn(tracking);

        var response = controller.updateTrackingLive("TRK-1", request);

        assertEquals(tracking, response.getBody().getData());
    }

    @Test
    void updateStatusDelegatesToService() {
        ShipmentResponse shipment = sampleShipmentResponse();
        when(service.updateStatus("TRK-1", "enviado")).thenReturn(shipment);

        var response = controller.updateStatus("TRK-1", "enviado");

        assertEquals(shipment, response.getBody().getData());
    }

    private ShipmentResponse sampleShipmentResponse() {
        return ShipmentResponse.builder()
                .trackingNumber("TRK-1")
                .orderNumber("ORD-1")
                .carrier("fast")
                .courierName("Repartidor fast")
                .status("proceso")
                .eta("48h")
                .build();
    }

    private ShipmentTrackingResponse sampleTrackingResponse() {
        return ShipmentTrackingResponse.builder()
                .trackingNumber("TRK-1")
                .orderNumber("ORD-1")
                .orderStatus("proceso")
                .shipmentStatus("proceso")
                .shippingAddress("Providencia 123, Santiago")
                .courierName("Repartidor fast")
                .routeGeoJson("{\"type\":\"LineString\"}")
                .originLat(-33.4)
                .originLng(-70.6)
                .destinationLat(-33.5)
                .destinationLng(-70.7)
                .courierLat(-33.45)
                .courierLng(-70.65)
                .progressPercent(40)
                .totalDurationSec(3600)
                .remainingDurationSec(2160)
                .totalDurationText("1h")
                .remainingDurationText("36m")
                .startedAt("2026-06-09T10:00:00")
                .routeSteps(List.of(ShipmentRouteStepEtaResponse.builder().build()))
                .lastUpdate("2026-06-09T10:05:00")
                .build();
    }
}