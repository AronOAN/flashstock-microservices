package com.order.controllers;

import com.order.common.ApiResponse;
import com.order.common.VerifiedCustomer;
import com.order.dtos.ReceiptEmailRequest;
import com.order.services.ReceiptDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {
    private final ReceiptDataService receiptDataService;

    @GetMapping("/from-orders")
    public ResponseEntity<ApiResponse<ReceiptEmailRequest>> getReceiptFromOrders(
            @RequestParam String orderNumbers,
            Authentication authentication
    ) {
        String customerSub = VerifiedCustomer.sub(authentication);

        List<String> numbers = Arrays.stream(orderNumbers.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        ReceiptEmailRequest receipt = receiptDataService.buildFromOrderNumbers(numbers, customerSub);
        return ResponseEntity.ok(ApiResponse.<ReceiptEmailRequest>builder()
                .message("Boleta generada")
                .data(receipt)
                .build());
    }

}
