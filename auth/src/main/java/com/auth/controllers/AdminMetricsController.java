package com.auth.controllers;

import com.auth.common.ApiResponse;
import com.auth.dtos.AdminMetricsResponse;
import com.auth.services.admin.AdminMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService metricsService;

    @GetMapping("/metrics")
    public ApiResponse<AdminMetricsResponse> metrics(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administrador");
        }

        return ApiResponse.<AdminMetricsResponse>builder()
                .message("Metricas administrativas en tiempo real")
                .data(metricsService.getAdminMetrics())
                .build();
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
