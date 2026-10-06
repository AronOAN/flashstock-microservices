package com.auth.tokens;

import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("aws")
@ConditionalOnProperty(name="flashstock.tokens.enabled", havingValue="true")
@RequestMapping("/api/auth/browser")
public class BackendOwnedTokenController {
    private static final String GROUPS = "cognito:groups";
    private final TokenSessionService sessions;
    private final FlashstockJwtService jwt;
    private final String siteOrigin;
    private final byte[] bffSecret;
    private final boolean secure;

    public BackendOwnedTokenController(TokenSessionService sessions, FlashstockJwtService jwt,
            @Value("${flashstock.browser.site-origin}") String siteOrigin,
            @Value("${flashstock.browser.bff-secret-base64}") String bffSecretBase64) {
        this.sessions = sessions;
        this.jwt = jwt;
        if (!siteOrigin.matches("https://[a-zA-Z0-9.-]+(:[0-9]{1,5})?")
                && !siteOrigin.matches("http://localhost(:[0-9]{1,5})?")) {
            throw new IllegalArgumentException("Invalid FlashStock site origin");
        }
        this.siteOrigin = siteOrigin;
        this.secure = siteOrigin.startsWith("https://");
        try { this.bffSecret = Base64.getDecoder().decode(bffSecretBase64); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid BFF secret", ex); }
        if (this.bffSecret.length < 32) throw new IllegalStateException("BFF secret needs at least 256 bits");
    }

    private void trusted(HttpServletRequest request) {
        String sent = request.getHeader("X-Flashstock-BFF-Secret");
        byte[] proposed = new byte[0];
        if (sent != null && sent.length() <= 256) {
            try { proposed = Base64.getDecoder().decode(sent); }
            catch (IllegalArgumentException ignored) { /* reject below */ }
        }
        if (!MessageDigest.isEqual(bffSecret, proposed)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!siteOrigin.equals(request.getHeader("Origin"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void requireMember(Jwt cognito) {
        List<String> groups = cognito == null ? null : cognito.getClaimAsStringList(GROUPS);
        if (groups == null || (!groups.contains("USER") && !groups.contains("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String cookieName(boolean refresh) {
        return (secure ? "__Host-flashstock-issued-" : "flashstock-issued-") + (refresh ? "refresh" : "access");
    }

    private ResponseCookie tokenCookie(boolean refresh, String value, long seconds) {
        return ResponseCookie.from(cookieName(refresh), value).httpOnly(true).secure(secure)
                .path("/").sameSite("Lax").maxAge(seconds).build();
    }

    private ResponseEntity<Map<String,Object>> cookieResponse(TokenSessionService.TokenPair pair) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, tokenCookie(false, pair.accessToken(), pair.expiresIn()).toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookie(true, pair.refreshToken(), pair.refreshExpiresIn()).toString())
                .body(Map.of("ok", true, "expiresIn", pair.expiresIn()));
    }

    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        for (var cookie : request.getCookies()) {
            if (cookieName(true).equals(cookie.getName()) && cookie.getValue() != null
                    && cookie.getValue().length() >= 20 && cookie.getValue().length() <= 8192) return cookie.getValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    @PostMapping(value="/exchange", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> exchange(@AuthenticationPrincipal Jwt cognito, HttpServletRequest request) {
        trusted(request);
        requireMember(cognito);
        return cookieResponse(sessions.exchange(cognito));
    }

    @PostMapping("/authorize")
    public ResponseEntity<Void> authorize(@AuthenticationPrincipal Jwt cognito, HttpServletRequest request) {
        trusted(request);
        requireMember(cognito);
        String raw = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) if (cookieName(false).equals(cookie.getName())) raw = cookie.getValue();
        }
        if (raw == null || raw.length() > 8192) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            var access = jwt.accessDecoder().decode(raw);
            List<String> roles = access.getClaimAsStringList("roles");
            if (!cognito.getSubject().equals(access.getSubject()) || roles == null || !roles.contains("ROLE_USER")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } catch (org.springframework.security.oauth2.jwt.JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping(value="/refresh", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> refresh(@AuthenticationPrincipal Jwt cognito, HttpServletRequest request) {
        trusted(request);
        requireMember(cognito);
        String raw = refreshCookie(request);
        try {
            if (!cognito.getSubject().equals(jwt.verifyRefresh(raw).getSubject())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return cookieResponse(sessions.refresh(raw));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(HttpServletRequest request) {
        trusted(request);
        try { sessions.revoke(refreshCookie(request)); }
        catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.UNAUTHORIZED) throw ex;
        }
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, tokenCookie(false, "", 0).toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookie(true, "", 0).toString()).build();
    }
}
