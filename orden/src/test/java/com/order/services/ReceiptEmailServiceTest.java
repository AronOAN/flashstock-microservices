package com.order.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.dtos.ReceiptEmailRequest;
import com.order.dtos.ReceiptLineItem;
import com.order.dtos.ReceiptShipmentInfo;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReceiptEmailServiceTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ReceiptEmailService service = new ReceiptEmailService(sender, new ObjectMapper());

    @BeforeEach
    void configureSmtpWithoutNetwork() {
        ReflectionTestUtils.setField(service, "mailHost", "smtp.example.test");
        ReflectionTestUtils.setField(service, "mailPort", 465);
        ReflectionTestUtils.setField(service, "mailFrom", "receipts@example.test");
        ReflectionTestUtils.setField(service, "mailPassword", "local-test-placeholder");
    }

    @Test
    void rejectsMissingRecipientOrSmtpConfigurationBeforeCreatingMessage() {
        ReceiptEmailRequest request = receipt();
        request.setCustomerEmail(" ");
        assertThrows(IllegalArgumentException.class, () -> service.sendReceiptEmail(request));

        request.setCustomerEmail("owner@example.test");
        ReflectionTestUtils.setField(service, "mailPassword", " ");
        assertThrows(IllegalStateException.class, () -> service.sendReceiptEmail(request));
        verifyNoInteractions(sender);
    }

    @Test
    void escapesEveryUntrustedFieldInHtmlWithoutSendingToTheNetwork() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        ReceiptEmailRequest request = receipt();
        request.setCustomerFirstName("<img src=x onerror=alert(1)>");
        request.setShippingAddress("A & B <script>alert(2)</script>");
        ReceiptLineItem item = new ReceiptLineItem();
        item.setProductName("<script>alert(3)</script>");
        item.setSku("X&Y");
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.TEN);
        item.setLineTotal(BigDecimal.valueOf(20));
        request.setItems(List.of(item));
        ReceiptShipmentInfo shipment = new ReceiptShipmentInfo();
        shipment.setOrderNumber("O-1");
        shipment.setTrackingNumber("<b>tracking</b>");
        request.setShipments(List.of(shipment));

        service.sendReceiptEmail(request);

        verify(sender).send(message);
        assertEquals("owner@example.test", message.getAllRecipients()[0].toString());
        assertEquals("boleta REC-1", message.getSubject());
        String html = (String) message.getContent();
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("A &amp; B &lt;script&gt;alert(2)&lt;/script&gt;"));
        assertTrue(html.contains("&lt;script&gt;alert(3)&lt;/script&gt;"));
        assertTrue(html.contains("X&amp;Y"));
        assertTrue(html.contains("&lt;b&gt;tracking&lt;/b&gt;"));
        assertFalse(html.contains("<script>alert(2)</script>"));
    }

    @Test
    void rendersMissingOptionalItemsAndShipmentRows() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        service.sendReceiptEmail(receipt());

        String html = (String) message.getContent();
        assertTrue(html.contains("Sin productos"));
        assertTrue(html.contains("Sin envios asociados"));
        assertTrue(html.contains("$0.00"));
    }

    private ReceiptEmailRequest receipt() {
        ReceiptEmailRequest request = new ReceiptEmailRequest();
        request.setReceiptNumber("REC-1");
        request.setCustomerEmail("owner@example.test");
        request.setShipping(BigDecimal.ZERO);
        request.setSubtotal(BigDecimal.TEN);
        request.setDiscount(BigDecimal.ZERO);
        request.setTotal(BigDecimal.TEN);
        return request;
    }
}
