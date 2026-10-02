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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

    @Test
    void repairsInvalidRouteAndClampsFallbackCoordinatesToChile() {
        Shipment shipment = Shipment.builder().orderNumber("ORD-1").trackingNumber("TRK-1")
                .status("shipped").carrier("DHL").originLat(0.0).originLng(0.0)
                .destinationLat(0.0).destinationLng(0.0).build();
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1")
                .shippingAddress("Av. Providencia 1").status("enviado").build();
        when(shipments.findByTrackingNumber("TRK-1")).thenReturn(Optional.of(shipment));
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

        ShipmentTrackingResponse snapshot = service.getTrackingSnapshot("TRK-1");

        assertEquals(65, snapshot.getProgressPercent());
        assertTrue(snapshot.getOriginLat() < -17 && snapshot.getOriginLat() > -56);
        assertTrue(snapshot.getDestinationLng() < -66 && snapshot.getDestinationLng() > -110);
        assertEquals(1800, snapshot.getTotalDurationSec());
        assertEquals("Repartidor FlashStock", snapshot.getCourierName());
        assertNotNull(shipment.getRouteStepsJson());
        verify(shipments).save(shipment);
    }

    @Test
    void deliveredSnapshotHandlesBadStoredStepsAndHasNoRemainingDuration() {
        Shipment shipment = Shipment.builder().orderNumber("ORD-1").trackingNumber("TRK-1")
                .status("delivered").originLat(-33.4489).originLng(-70.6693)
                .destinationLat(-33.45).destinationLng(-70.67)
                .routeGeoJson("{\"type\":\"LineString\"}").routeStepsJson("invalid json")
                .build();
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1")
                .shippingAddress("Santiago, Chile").status("completado").build();
        when(shipments.findByTrackingNumber("TRK-1")).thenReturn(Optional.of(shipment));
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

        ShipmentTrackingResponse snapshot = service.getTrackingSnapshot("TRK-1");

        assertEquals(100, snapshot.getProgressPercent());
        assertEquals(0, snapshot.getRemainingDurationSec());
        assertTrue(snapshot.getRouteSteps().isEmpty());
        assertEquals(-33.45, snapshot.getCourierLat());
    }

    @Test
    void duplicateInsertReturnsShipmentCommittedByConcurrentRequest() {
        ShipmentRequest request = new ShipmentRequest();
        request.setOrderNumber("ORD-1");
        request.setCarrier("DHL");
        CustomerOrder order = CustomerOrder.builder().orderNumber("ORD-1")
                .shippingAddress("Santiago").status("proceso").build();
        Shipment concurrent = Shipment.builder().trackingNumber("TRK-CONCURRENT")
                .orderNumber("ORD-1").status("proceso").build();
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(shipments.findByOrderNumber("ORD-1")).thenReturn(Optional.empty(), Optional.of(concurrent));
        when(shipments.save(any(Shipment.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertEquals("TRK-CONCURRENT", service.create(request).getTrackingNumber());
        verify(shipments, times(1)).save(any(Shipment.class));
    }

    @Test
    void notFoundTrackingIsRejectedAndListOnlyContainsPersistedShipments() {
        when(shipments.findByTrackingNumber("UNKNOWN")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.findByTrackingNumber("UNKNOWN"));
        when(shipments.findAll()).thenReturn(List.of(Shipment.builder().orderNumber("ORD-1")
                .trackingNumber("TRK-1").status("proceso").build()));
        assertEquals("TRK-1", service.findAll().get(0).getTrackingNumber());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsForeignGeocodingResultAndFallsBackToChile() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> foreign = mock(HttpResponse.class);
        HttpResponse<String> noDirections = mock(HttpResponse.class);
        when(foreign.body()).thenReturn("""
                {"results":[{"address_components":[{"types":["country"],"short_name":"US"}],
                "geometry":{"location":{"lat":40.0,"lng":-100.0}}}]}
                """);
        when(noDirections.body()).thenReturn("{\"routes\":[]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(foreign, noDirections);
        ReflectionTestUtils.setField(service, "httpClient", client);
        ReflectionTestUtils.setField(service, "googleApiKey", "test-key");
        ReflectionTestUtils.setField(service, "googleGeocodingUrl", "https://maps.example.test/geocode");
        ReflectionTestUtils.setField(service, "googleDirectionsUrl", "https://maps.example.test/directions");
        ShipmentRequest request = new ShipmentRequest();
        request.setOrderNumber("ORD-1");
        request.setCarrier("DHL");
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(CustomerOrder.builder()
                .orderNumber("ORD-1").shippingAddress("Some US address").build()));
        when(shipments.save(any(Shipment.class))).thenAnswer(call -> call.getArgument(0));

        service.create(request);

        verify(shipments).save(argThat(s -> s.getDestinationLat() < -17
                && s.getDestinationLat() > -56 && s.getDestinationLng() < -66
                && s.getDestinationLng() > -110));
        verify(client, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void geocodingInterruptionRestoresInterruptFlagAndStillBuildsLocalRoute() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("test interrupt"));
        ReflectionTestUtils.setField(service, "httpClient", client);
        ReflectionTestUtils.setField(service, "googleApiKey", "test-key");
        ReflectionTestUtils.setField(service, "googleGeocodingUrl", "https://maps.example.test/geocode");
        ReflectionTestUtils.setField(service, "googleDirectionsUrl", "https://maps.example.test/directions");
        ShipmentRequest request = new ShipmentRequest();
        request.setOrderNumber("ORD-1");
        request.setCarrier("DHL");
        when(orders.findByOrderNumber("ORD-1")).thenReturn(Optional.of(CustomerOrder.builder()
                .orderNumber("ORD-1").shippingAddress("Santiago").build()));
        when(shipments.save(any(Shipment.class))).thenAnswer(call -> call.getArgument(0));

        try {
            assertNotNull(service.create(request).getTrackingNumber());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsChileLabeledCoordinatesOutsideChile() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> inconsistent = mock(HttpResponse.class);
        when(inconsistent.body()).thenReturn("""
                {"results":[{"address_components":[{"types":["country"],"short_name":"CL"}],
                "geometry":{"location":{"lat":40.0,"lng":-100.0}}}]}
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(inconsistent);
        ReflectionTestUtils.setField(service, "httpClient", client);
        ReflectionTestUtils.setField(service, "googleApiKey", "test-key");
        ReflectionTestUtils.setField(service, "googleGeocodingUrl", "https://maps.example.test/geocode");

        double[] coordinates = ReflectionTestUtils.invokeMethod(service, "geocodeAddress", "Santiago");

        assertNotNull(coordinates);
        assertTrue(coordinates[0] < -17 && coordinates[0] > -56);
        assertTrue(coordinates[1] < -66 && coordinates[1] > -110);
    }
}
