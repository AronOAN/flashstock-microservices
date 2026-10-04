package com.auth.tokens;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/** Auth owns RS256 credentials and refresh rotation. No raw JWT is returned to browser JS. */
@RestController
@Profile("aws")
@ConditionalOnProperty(name="flashstock.tokens.enabled", havingValue="true")
@RequestMapping("/api/auth/browser")
public class BackendOwnedTokenController {
    private final TokenSessionService sessions;
    private final FlashstockJwtService jwt;
    private final String siteOrigin;
    private final byte[] bffSecret;
    private final boolean secure;

    public BackendOwnedTokenController(TokenSessionService sessions, FlashstockJwtService jwt,
            @Value("${flashstock.browser.site-origin}") String siteOrigin,
            @Value("${flashstock.browser.bff-secret-base64}") String bffSecretBase64) {
        this.sessions=sessions; this.jwt=jwt;
        if (!siteOrigin.matches("https://[a-zA-Z0-9.-]+(:[0-9]{1,5})?")
                && !siteOrigin.matches("http://localhost(:[0-9]{1,5})?")) {
            throw new IllegalArgumentException("Invalid FlashStock site origin");
        }
        this.siteOrigin=siteOrigin;
        this.secure=siteOrigin.startsWith("https://");
        try { this.bffSecret=Base64.getDecoder().decode(bffSecretBase64); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid BFF secret",ex); }
        if (this.bffSecret.length<32) throw new IllegalStateException("BFF secret needs at least 256 bits");
    }

    private void trusted(HttpServletRequest request) {
        String sent=request.getHeader("X-Flashstock-BFF-Secret");
        byte[] proposed=new byte[0];
        if (sent!=null && sent.length()<=256) {
            try { proposed=Base64.getDecoder().decode(sent); }
            catch (IllegalArgumentException ignored) { /* reject */ }
        }
        if (!MessageDigest.isEqual(bffSecret,proposed)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!siteOrigin.equals(request.getHeader("Origin"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        // The public HTTP API never accepts an untrusted browser's Origin as authentication:
        // the independent server-only secret is required as well.
    }
    private String cookieName(boolean refresh) {
        String suffix=refresh?"refresh":"access";
        return (secure?"__Host-flashstock-issued-":"flashstock-issued-")+suffix;
    }
    private ResponseCookie tokenCookie(boolean refresh,String value,long seconds) {
        return ResponseCookie.from(cookieName(refresh),value).httpOnly(true).secure(secure)
                .path("/").sameSite("Lax").maxAge(seconds).build();
    }
    private ResponseEntity<Map<String,Object>> cookieResponse(TokenSessionService.TokenPair pair) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.PRAGMA,"no-cache")
                .header(HttpHeaders.SET_COOKIE,tokenCookie(false,pair.accessToken(),pair.expiresIn()).toString())
                .header(HttpHeaders.SET_COOKIE,tokenCookie(true,pair.refreshToken(),pair.refreshExpiresIn()).toString())
                .body(Map.of("ok",true,"expiresIn",pair.expiresIn()));
    }
    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        for (var c:request.getCookies()) if (cookieName(true).equals(c.getName()) && c.getValue()!=null
                && c.getValue().length()>=20 && c.getValue().length()<=8192) return c.getValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    /** The gateway requires Cognito JWT; Spring ALSO checks ROLE_ADMIN and Cognito claims. */
    @PostMapping(value="/exchange", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> exchange(@AuthenticationPrincipal Jwt cognito,HttpServletRequest request) {
        trusted(request);
        if (cognito==null || cognito.getClaimAsStringList("cognito:groups")==null
                || !cognito.getClaimAsStringList("cognito:groups").contains("ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return cookieResponse(sessions.exchange(cognito));
    }

    /** Verify both issuers and bind the already-verified Cognito sub to our ADMIN access cookie. */
    @PostMapping("/authorize")
    public ResponseEntity<Void> authorize(@AuthenticationPrincipal Jwt cognito, HttpServletRequest request) {
        trusted(request);
        if (cognito==null || cognito.getClaimAsStringList("cognito:groups")==null
                || !cognito.getClaimAsStringList("cognito:groups").contains("ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String raw=null;
        if (request.getCookies()!=null) for (var cookie:request.getCookies())
            if (cookieName(false).equals(cookie.getName())) raw=cookie.getValue();
        if (raw==null || raw.length()>8192) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            var access = jwt.accessDecoder().decode(raw);
            if (!cognito.getSubject().equals(access.getSubject())
                    || access.getClaimAsStringList("roles")==null
                    || !access.getClaimAsStringList("roles").contains("ROLE_ADMIN"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (org.springframework.security.oauth2.jwt.JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL,"no-store").build();
    }

    /** Signed, single-use refresh backed by row locks and family revocation. No JSON refresh input. */
    @PostMapping(value="/refresh", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> refresh(@AuthenticationPrincipal Jwt cognito, HttpServletRequest request) {
        trusted(request);
        // A fresh, verified Cognito ADMIN token is mandatory on EACH rotation. No stale role snapshot.
        if (cognito==null || cognito.getClaimAsStringList("cognito:groups")==null
                || !cognito.getClaimAsStringList("cognito:groups").contains("ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String raw=refreshCookie(request);
        try {
            if (!cognito.getSubject().equals(jwt.verifyRefresh(raw).getSubject()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return cookieResponse(sessions.refresh(raw));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(HttpServletRequest request) {
        trusted(request);
        try { sessions.revoke(refreshCookie(request)); }
        catch (ResponseStatusException ex) {
            if (ex.getStatusCode()!=HttpStatus.UNAUTHORIZED) throw ex;
            // Invalid or already revoked token: still expire browser cookies.
        }
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header(HttpHeaders.SET_COOKIE,tokenCookie(false,"",0).toString())
                .header(HttpHeaders.SET_COOKIE,tokenCookie(true,"",0).toString()).build();
    }
}
