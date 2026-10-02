package com.auth.models;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentTimestampsTest {
    @Test
    void initializesUtcTimestampsAndKeepsOriginalCreationTimeOnUpdate() {
        Shipment shipment = new Shipment();
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        ReflectionTestUtils.invokeMethod(shipment, "touchLastUpdate");
        LocalDateTime created = shipment.getCreatedAt();
        assertNotNull(created);
        assertFalse(created.isBefore(before));
        assertFalse(created.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
        assertNotNull(shipment.getLastUpdate());

        ReflectionTestUtils.invokeMethod(shipment, "touchLastUpdate");
        assertEquals(created, shipment.getCreatedAt());
        assertFalse(shipment.getLastUpdate().isBefore(created));
    }
}
