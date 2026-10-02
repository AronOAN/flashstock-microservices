package com.shipping.services;

import com.shipping.dtos.ShipmentLiveUpdateRequest;
import com.shipping.dtos.ShipmentRequest;
import com.shipping.dtos.ShipmentResponse;
import com.shipping.dtos.ShipmentTrackingResponse;
import com.shipping.models.CustomerOrder;
import com.shipping.models.Shipment;
import com.shipping.repos.OrderRepository;
import com.shipping.repos.ShipmentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShippingServiceTest {
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ShippingService service = new ShippingService(shipments, orders);

    @Test
    void createsDeterministicLocalRouteWhenMapsAreNotConfigured() {
        ShipmentRequest request = new ShipmentRequest();
        request.setOrderNumber("ORD-1");
        request.setCarrier("DHL");
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1")
                .shippingAddress("Santiago, Chile").status("proceso").build();
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(shipments.findByOrderNumber("ORD-1")).thenReturn(Optional.empty());
        when(shipments.save(any(Shipment.class))).thenAnswer(call -> call.getArgument(0));

        ShipmentResponse response = service.create(request);

        assertTrue(response.getTrackingNumber().startsWith("TRK-"));
        assertEquals("proceso", response.getStatus());
        verify(shipments).save(argThat(shipment -> shipment.getRouteGeoJson().contains("LineString")
                && shipment.getTotalDurationSec() == 1800
                && shipment.getDestinationLat() != null));
    }

    @Test
    void duplicateRequestReturnsExistingTrackingWithoutCreatingAnother() {
        ShipmentRequest request = new ShipmentRequest();
        request.setOrderNumber("ORD-1");
        request.setCarrier("DHL");
        when(shipments.findByOrderNumber("ORD-1")).thenReturn(Optional.of(
                Shipment.builder().orderNumber("ORD-1").trackingNumber("TRK-1").status("proceso").build()));

        assertEquals("TRK-1", service.create(request).getTrackingNumber());
        verify(shipments, never()).save(any());
        verifyNoInteractions(orders);
    }

    @Test
    void deliveryStatusMovesCourierAndSynchronizesOrder() {
        Shipment shipment = Shipment.builder().orderNumber("ORD-1").trackingNumber("TRK-1")
                .status("proceso").originLat(-33.4).originLng(-70.6)
                .destinationLat(-33.5).destinationLng(-70.7).build();
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1").status("proceso").build();
        when(shipments.findByTrackingNumber("TRK-1")).thenReturn(Optional.of(shipment));
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(shipments.save(shipment)).thenReturn(shipment);

        ShipmentResponse response = service.updateStatus("TRK-1", "delivered");

        assertEquals("delivered", response.getStatus());
        assertEquals("completado", order.getStatus());
        assertEquals(-33.5, shipment.getCourierLat());
        verify(orders).save(order);
    }

    @Test
    void trackingUpdateClampsCoordinatesAndReturnsSnapshot() {
        Shipment shipment = Shipment.builder().orderNumber("ORD-1").trackingNumber("TRK-1")
                .status("proceso").carrier("DHL").originLat(-33.4).originLng(-70.6)
                .destinationLat(-33.5).destinationLng(-70.7)
                .routeGeoJson("{\"type\":\"LineString\"}").totalDurationSec(1800).build();
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1")
                .shippingAddress("Santiago, Chile").status("proceso").build();
        when(shipments.findByTrackingNumber("TRK-1")).thenReturn(Optional.of(shipment));
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        ShipmentLiveUpdateRequest input = new ShipmentLiveUpdateRequest();
        input.setCourierName("Ana");
        input.setCourierLat(500.0);
        input.setCourierLng(-500.0);

        ShipmentTrackingResponse tracking = service.updateTrackingLive("TRK-1", input);

        assertEquals("Ana", tracking.getCourierName());
        assertTrue(tracking.getCourierLat() <= -17.0 && tracking.getCourierLat() >= -56.0);
        assertTrue(tracking.getCourierLng() <= -66.0 && tracking.getCourierLng() >= -110.0);
        assertEquals(15, tracking.getProgressPercent());
        assertEquals(1800, tracking.getTotalDurationSec());
    }
}
