package com.shipping.models;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryDriverTimestampsTest {
    @Test
    void updatesTimestampInUtcWhenPersistedOrChanged() {
        DeliveryDriver driver = new DeliveryDriver();
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        ReflectionTestUtils.invokeMethod(driver, "touchUpdatedAt");
        assertNotNull(driver.getUpdatedAt());
        assertFalse(driver.getUpdatedAt().isBefore(before));
        assertFalse(driver.getUpdatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    }
}
