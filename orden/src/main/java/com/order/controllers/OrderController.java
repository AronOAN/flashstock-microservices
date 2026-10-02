package com.order.controllers;

import com.order.common.ApiResponse;
import com.order.common.VerifiedCustomer;
import com.order.dtos.OrderCustomerShippingResponse;
import com.order.dtos.OrderRequest;
import com.order.dtos.OrderResponse;
import com.order.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAll(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.<List<OrderResponse>>builder().message("Pedidos listados").data(service.findAll()).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderRequest request, Authentication authentication) {
        String customerSub = VerifiedCustomer.sub(authentication);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder().message("Pedido creado")
                .data(service.create(request, customerSub, VerifiedCustomer.verifiedEmail(authentication))).build());
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getByOrderNumber(@PathVariable String orderNumber, Authentication authentication) {
        String customerSub = VerifiedCustomer.sub(authentication);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder().message("Pedido encontrado")
                .data(service.findByOrderNumber(orderNumber, customerSub, VerifiedCustomer.isAdmin(authentication))).build());
    }

    @PatchMapping("/{orderNumber}/status/{status}")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@PathVariable String orderNumber, @PathVariable String status,
                                                                    Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder().message("Estado actualizado").data(service.updateStatus(orderNumber, status)).build());
    }

    @GetMapping("/customer-shipping")
    public ResponseEntity<ApiResponse<List<OrderCustomerShippingResponse>>> getCustomerShipping(
            Authentication authentication,
            @RequestParam(required = false) String status
    ) {
        requireAdmin(authentication);

        return ResponseEntity.ok(ApiResponse.<List<OrderCustomerShippingResponse>>builder()
                .message("Pedido + envio + cliente")
                .data(service.findCustomerOrderShipping(status))
                .build());
    }

    @GetMapping("/my-history")
    public ResponseEntity<ApiResponse<List<OrderCustomerShippingResponse>>> getMyHistory(
            Authentication authentication,
            @RequestParam(required = false) String status
    ) {
        String customerSub = VerifiedCustomer.sub(authentication);

        return ResponseEntity.ok(ApiResponse.<List<OrderCustomerShippingResponse>>builder()
                .message("Mis pedidos")
                .data(service.findMyOrderHistory(customerSub, status))
                .build());
    }

    @PostMapping("/{orderNumber}/confirm-received")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmReceived(
            Authentication authentication,
            @PathVariable String orderNumber
    ) {
        String customerSub = VerifiedCustomer.sub(authentication);

        return ResponseEntity.ok(ApiResponse.<OrderResponse>builder()
                .message("Pedido confirmado como recibido")
                .data(service.confirmReceived(orderNumber, customerSub))
                .build());
    }

    private void requireAdmin(Authentication authentication) {
        if (!VerifiedCustomer.isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administrador");
        }
    }
}
