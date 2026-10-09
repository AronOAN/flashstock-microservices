
package com.auth.bff;

import com.auth.common.ApiResponse;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/session")
public class BffCsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Map<String, String>>>
            csrf(CsrfToken csrfToken) {

        // Proviene de Spring Security.
        // Nunca se acepta un token inventado por el usuario.
        String token = csrfToken.getToken();

        var response =
            ApiResponse.<Map<String, String>>builder()
                .message("Token CSRF")
                .data(Map.of("csrfToken", token))
                .build();

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(response);
    }
}
