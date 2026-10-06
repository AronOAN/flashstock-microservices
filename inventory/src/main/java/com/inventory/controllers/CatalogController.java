package com.inventory.controllers;

import com.inventory.common.ApiResponse;
import com.inventory.dtos.CatalogItemResponse;
import com.inventory.services.CatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CatalogItemResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.<List<CatalogItemResponse>>builder()
                .message("Catalogo publico")
                .data(service.findAll())
                .build());
    }
}
