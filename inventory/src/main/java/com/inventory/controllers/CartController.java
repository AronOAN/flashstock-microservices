package com.inventory.controllers;

import com.inventory.common.ApiResponse;
import com.inventory.dtos.CartItemMutationRequest;
import com.inventory.dtos.CartItemResponse;
import com.inventory.services.CartService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> list(Authentication authentication) {
        return ok("Carrito", service.list(authentication));
    }

    @PostMapping("/items/{sku}")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> add(Authentication authentication,
            @PathVariable String sku, @Valid @RequestBody CartItemMutationRequest request) {
        return ok("Producto agregado", service.add(authentication, sku, request.getQuantity()));
    }

    @PatchMapping("/items/{sku}")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> setQuantity(Authentication authentication,
            @PathVariable String sku, @Valid @RequestBody CartItemMutationRequest request) {
        return ok("Cantidad actualizada", service.setQuantity(authentication, sku, request.getQuantity()));
    }

    @DeleteMapping("/items/{sku}")
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> remove(Authentication authentication, @PathVariable String sku) {
        return ok("Producto eliminado", service.remove(authentication, sku));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<List<CartItemResponse>>> clear(Authentication authentication) {
        return ok("Carrito limpiado", service.clear(authentication));
    }

    private ResponseEntity<ApiResponse<List<CartItemResponse>>> ok(String message, List<CartItemResponse> data) {
        return ResponseEntity.ok(ApiResponse.<List<CartItemResponse>>builder().message(message).data(data).build());
    }
}
