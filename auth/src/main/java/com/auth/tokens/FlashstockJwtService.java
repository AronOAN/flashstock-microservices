package com.auth.tokens;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Private RSA key NEVER leaves auth; consumers verify with its public counterpart. */
@Service
@Profile("aws")
@ConditionalOnProperty(name="flashstock.tokens.enabled", havingValue="true")
public class FlashstockJwtService {
    public record Issued(String accessToken, String refreshToken, String refreshJti,
                         String familyId, Instant refreshExpiresAt) { }
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer, audience, refreshAudience, keyId;
    private final long accessSeconds, refreshSeconds;

    public FlashstockJwtService(
            @Value("${flashstock.tokens.private-key-der-base64}") String privateDer,
            @Value("${flashstock.tokens.public-key-der-base64}") String publicDer,
            @Value("${flashstock.tokens.issuer}") String issuer,
            @Value("${flashstock.tokens.audience}") String audience,
            @Value("${flashstock.tokens.refresh-audience}") String refreshAudience,
            @Value("${flashstock.tokens.key-id}") String keyId,
            @Value("${flashstock.tokens.access-seconds}") long accessSeconds,
            @Value("${flashstock.tokens.refresh-seconds}") long refreshSeconds) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey privatePart = factory.generatePrivate(new PKCS8EncodedKeySpec(Decoders.BASE64.decode(privateDer)));
            this.privateKey = (RSAPrivateKey) privatePart;
            this.publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Decoders.BASE64.decode(publicDer)));
            if (this.privateKey.getModulus().bitLength() < 2048 || this.publicKey.getModulus().bitLength() < 2048
                    || !this.privateKey.getModulus().equals(this.publicKey.getModulus())) {
                throw new IllegalArgumentException("RSA keypair mismatch or RSA modulus smaller than 2048 bits");
            }
            if (accessSeconds < 60 || accessSeconds > 3600 || refreshSeconds <= accessSeconds
                    || refreshSeconds > 2592000) {
                throw new IllegalArgumentException("Invalid token lifetimes");
            }
            if (issuer.isBlank() || audience.isBlank() || refreshAudience.isBlank() || audience.equals(refreshAudience)
                    || keyId.isBlank()) throw new IllegalArgumentException("Invalid issuer/audience/kid");
            this.issuer=issuer; this.audience=audience; this.refreshAudience=refreshAudience;
            this.keyId=keyId; this.accessSeconds=accessSeconds; this.refreshSeconds=refreshSeconds;
            // Fail fast if private/public keys are not an actual signing pair.
            
            String probe = Jwts.builder().subject("keypair-self-test").signWith(this.privateKey, Jwts.SIG.RS256).compact();

            Jwts.parser().verifyWith(this.publicKey).build().parseSignedClaims(probe);

        } catch (Exception ex) {
            throw new IllegalStateException("FlashStock RSA signing key configuration invalid", ex);
        }
    }

    public Issued issue(String subject, String username, List<String> roles, String family, Instant familyExpiresAt) {
        if (subject == null || subject.isBlank() || username == null || username.isBlank()
                || roles == null || roles.isEmpty() || roles.stream().anyMatch(r -> !r.equals("ROLE_USER") && !r.equals("ROLE_ADMIN"))) {
            throw new IllegalArgumentException("Missing or untrusted authenticated identity");
        }
        Instant now=Instant.now();
        if (!familyExpiresAt.isAfter(now)) throw new IllegalArgumentException("Expired refresh family");
        String refreshJti=UUID.randomUUID().toString();
        String access=Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer).subject(subject).audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .claim("username",username).claim("roles",List.copyOf(roles))
                .claim("created_at",now.toString()).claim("created_by",issuer).claim("token_type","access")
                .issuedAt(Date.from(now)).notBefore(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessSeconds)))
                .signWith(privateKey,Jwts.SIG.RS256).compact();
                
        String refresh=Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer).subject(subject).audience().add(refreshAudience).and()
                .id(refreshJti).claim("token_type","refresh").claim("family_id",family)
                // No roles/credentials in refresh JWT. Roles are sourced from locked DB row.
                .issuedAt(Date.from(now)).notBefore(Date.from(now)).expiration(Date.from(familyExpiresAt))
                .signWith(privateKey,Jwts.SIG.RS256).compact();
        return new Issued(access,refresh,refreshJti,family,familyExpiresAt);
    }

    public Claims verifyRefresh(String raw) {
        Jws<Claims> signed=Jwts.parser().verifyWith(publicKey)
                .requireIssuer(issuer).requireAudience(refreshAudience).require("token_type","refresh")
                .build().parseSignedClaims(raw);
        Claims claims=signed.getPayload();
        if (!"RS256".equals(signed.getHeader().getAlgorithm()) || !keyId.equals(signed.getHeader().getKeyId())
                || claims.getId()==null || claims.getSubject()==null || claims.getSubject().isBlank()
                || claims.get("family_id",String.class)==null || claims.getIssuedAt()==null
                || claims.getExpiration()==null) throw new JwtException("Invalid refresh token header/claims");
        return claims;
    }

    public JwtDecoder accessDecoder() {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> accessValidator=jwt -> {
            List<String> aud=jwt.getAudience();
            List<String> roles=jwt.getClaimAsStringList("roles");
            if (!issuer.equals(jwt.getClaimAsString("iss")) || aud==null || !aud.contains(audience) || !"access".equals(jwt.getClaimAsString("token_type"))
                    || jwt.getSubject()==null || jwt.getSubject().isBlank() || jwt.getIssuedAt()==null
                    || jwt.getExpiresAt()==null || jwt.getId()==null || jwt.getId().isBlank()
                    || roles==null || roles.isEmpty()
                    || roles.stream().anyMatch(r -> !r.equals("ROLE_USER") && !r.equals("ROLE_ADMIN"))
                    || !keyId.equals(jwt.getHeaders().get("kid"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid FlashStock access claims",null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),accessValidator));
        return decoder;
    }
    public String issuer(){return issuer;}
    public long accessSeconds(){return accessSeconds;}
    public long refreshSeconds(){return refreshSeconds;}
}
