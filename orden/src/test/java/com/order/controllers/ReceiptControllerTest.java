package com.order.controllers;

import com.order.services.ReceiptDataService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ReceiptControllerTest {
    @Test
    void buildReceiptUsesVerifiedSubjectForEveryOrder() {
        ReceiptDataService service = mock(ReceiptDataService.class);
        ReceiptController controller = new ReceiptController(service);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject("buyer-1").claim("token_use", "access").build();
        controller.getReceiptFromOrders("ORD-1,ORD-2",
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")), "buyer-1"));
        verify(service).buildFromOrderNumbers(List.of("ORD-1", "ORD-2"), "buyer-1");
    }

    @Test
    void anonymousCannotReadReceipt() {
        ReceiptDataService service = mock(ReceiptDataService.class);
        ReceiptController controller = new ReceiptController(service);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.getReceiptFromOrders("ORD-1", null)).getStatusCode());
        verifyNoInteractions(service);
    }
}
