package com.auth.tokens;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlashstockTokenFlowTest {
    private static final String COGNITO = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test";
    private FlashstockJwtService jwt;
    private TokenSessionService sessions;
    private JdbcTemplate db;

    @BeforeEach void setUp() throws Exception {
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair=gen.generateKeyPair();
        jwt=new FlashstockJwtService(
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                "flashstock-auth","flashstock-api","flashstock-auth-refresh","flashstock-rs256-v1",900,86400);
        JdbcDataSource dataSource=new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jwt_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        db=new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE flashstock_refresh_families (family_id VARCHAR(36) PRIMARY KEY, expires_at BIGINT NOT NULL, revoked_at BIGINT)");
        db.execute("CREATE TABLE flashstock_refresh_sessions (jti VARCHAR(36) PRIMARY KEY, family_id VARCHAR(36) NOT NULL " +
                "REFERENCES flashstock_refresh_families(family_id), subject_id VARCHAR(255) NOT NULL, username VARCHAR(255) NOT NULL, " +
                "roles VARCHAR(128) NOT NULL, token_sha256 VARCHAR(64) NOT NULL UNIQUE, expires_at BIGINT NOT NULL, used_at BIGINT, revoked_at BIGINT)");
        sessions=new TokenSessionService(jwt,db,new TransactionTemplate(new DataSourceTransactionManager(dataSource)),COGNITO);
    }

    private Jwt cognito(List<String> groups, String issuer, String tokenType) {
        Instant now=Instant.now();
        return Jwt.withTokenValue("already-verified-by-Cognito-decoder")
                .header("alg","RS256").subject("cognito-stable-sub")
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("iss",issuer).claim("token_use",tokenType)
                .claim("cognito:groups",groups).claim("username","aron")
                .build();
    }

    @Test void signsExactAccessClaimsAndRotatesSingleUseRefresh() {
        var initial=sessions.exchange(cognito(List.of("ADMIN"),COGNITO,"access"));
        var access=jwt.accessDecoder().decode(initial.accessToken());
        assertEquals("RS256",access.getHeaders().get("alg"));
        assertEquals("flashstock-auth",access.getClaimAsString("iss"));
        assertEquals("cognito-stable-sub",access.getSubject());
        assertEquals(List.of("flashstock-api"),access.getAudience());
        assertEquals(List.of("ROLE_USER","ROLE_ADMIN"),access.getClaimAsStringList("roles"));
        assertEquals("aron",access.getClaimAsString("username"));
        assertEquals("flashstock-auth",access.getClaimAsString("created_by"));
        assertNotNull(access.getClaimAsString("created_at"));
        assertEquals("access",access.getClaimAsString("token_type"));
        assertNotNull(access.getId()); assertNotNull(access.getIssuedAt()); assertNotNull(access.getExpiresAt());
        assertEquals("refresh",jwt.verifyRefresh(initial.refreshToken()).get("token_type"));
        assertThrows(RuntimeException.class,()->jwt.accessDecoder().decode(initial.refreshToken()));
        var next=sessions.refresh(initial.refreshToken());
        assertNotEquals(initial.accessToken(),next.accessToken());
        assertNotEquals(initial.refreshToken(),next.refreshToken());
        // Replay revokes entire family, not just the used row.
        assertThrows(ResponseStatusException.class,()->sessions.refresh(initial.refreshToken()));
        assertThrows(ResponseStatusException.class,()->sessions.refresh(next.refreshToken()));
    }

    @Test void revokeBlocksRemainingRefresh() {
        var initial=sessions.exchange(cognito(List.of("ADMIN"),COGNITO,"access"));
        assertEquals(List.of("ROLE_USER","ROLE_ADMIN"),jwt.accessDecoder().decode(initial.accessToken()).getClaimAsStringList("roles"));
        sessions.revoke(initial.refreshToken());
        assertThrows(ResponseStatusException.class,()->sessions.refresh(initial.refreshToken()));
    }

    @Test void refusesForgedRoleAndWrongIssuerAndWrongType() {
        assertThrows(ResponseStatusException.class,()->sessions.exchange(cognito(List.of("ROLE_ADMIN"),COGNITO,"access")));
        assertThrows(ResponseStatusException.class,()->sessions.exchange(cognito(List.of("ADMIN"),"attacker","access")));
        assertThrows(ResponseStatusException.class,()->sessions.exchange(cognito(List.of("ADMIN"),COGNITO,"id")));
        assertThrows(ResponseStatusException.class,()->sessions.exchange(cognito(List.of("USER"),COGNITO,"access")));
        var issued=sessions.exchange(cognito(List.of("ADMIN"),COGNITO,"access"));
        assertThrows(ResponseStatusException.class,()->sessions.refresh(issued.accessToken()));
        String tampered=issued.refreshToken().substring(0,issued.refreshToken().length()-2)+"XX";
        assertThrows(ResponseStatusException.class,()->sessions.refresh(tampered));
    }

    @Test void publicKeyFromAnotherIssuerCannotVerifyAccess() throws Exception {
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048); KeyPair pair=gen.generateKeyPair();
        var other=new FlashstockJwtService(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),"flashstock-auth","flashstock-api",
                "flashstock-auth-refresh","flashstock-rs256-v1",900,86400);
        var issued=sessions.exchange(cognito(List.of("ADMIN"),COGNITO,"access"));
        assertThrows(RuntimeException.class,()->other.accessDecoder().decode(issued.accessToken()));
    }
}
