
package com.auth.tokens;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@Profile("aws")
@ConditionalOnProperty(
    name = "flashstock.hybrid.enabled",
    havingValue = "true"
)
public class RefreshCookieService {

    public static final String COOKIE_NAME =
        "__Host-flashstock-refresh";

    private static final SecureRandom RANDOM =
        new SecureRandom();

    private static final int HANDLE_BYTES = 32;

    private final Duration lifetime;

    public RefreshCookieService(
        @Value("${flashstock.hybrid.refresh.max-age-seconds:86400}")
        long maxAgeSeconds
    ) {
        if (
            maxAgeSeconds < 300 ||
            maxAgeSeconds > 604800
        ) {
            throw new IllegalArgumentException(
                "Duración del refresh inválida"
            );
        }

        this.lifetime = Duration.ofSeconds(maxAgeSeconds);
    }

    // Genera un identificador aleatorio de 256 bits.
    public String generateHandle() {
        byte[] random = new byte[HANDLE_BYTES];
        RANDOM.nextBytes(random);

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(random);
    }

    // Hash que se guardará en la base de datos.
    public String hashHandle(String handle) {
        if (!isValidHandle(handle)) {
            throw new IllegalArgumentException(
                "Identificador inválido"
            );
        }

        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                handle.getBytes(StandardCharsets.US_ASCII)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                "SHA-256 no disponible",
                ex
            );
        }
    }

    // Instalar cookie después de un login correcto.
    public void write(
        HttpServletResponse response,
        String handle
    ) {
        if (!isValidHandle(handle)) {
            throw new IllegalArgumentException(
                "Identificador inválido"
            );
        }

        ResponseCookie cookie = ResponseCookie
            .from(COOKIE_NAME, handle)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(lifetime)
            .build();

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookie.toString()
        );

        response.setHeader(
            HttpHeaders.CACHE_CONTROL,
            "no-store"
        );
    }

    // Auth lee la cookie durante el refresh.
    // Nunca se envía este identificador en JSON.
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        String found = null;

        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }

            // Rechazar cookies duplicadas.
            if (found != null) {
                return null;
            }

            String value = cookie.getValue();

            if (!isValidHandle(value)) {
                return null;
            }

            found = value;
        }

        return found;
    }

    // Eliminar cookie en logout o sesión revocada.
    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
            .from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookie.toString()
        );

        response.setHeader(
            HttpHeaders.CACHE_CONTROL,
            "no-store"
        );
    }

    private boolean isValidHandle(String value) {
        return value != null &&
            value.matches("[A-Za-z0-9_-]{43}");
    }
}
