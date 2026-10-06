package com.auth.tokens;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("aws")
@ConditionalOnProperty(name="flashstock.tokens.enabled", havingValue="true")
public class TokenSessionService {
    public record TokenPair(String accessToken, String refreshToken, String tokenType,long expiresIn,long refreshExpiresIn) { }
    private enum Status { OK, INVALID, REUSED }
    private record Outcome(Status status, TokenPair pair) { }
    private record Family(long expiry, Long revoked) { }
    private record Row(String family,String subject,String username,String roles,String hash,long expires,Long used,Long revoked) { }

    private final FlashstockJwtService jwt;
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final String cognitoIssuer;

    public TokenSessionService(FlashstockJwtService jwt, JdbcTemplate db, TransactionTemplate transaction,
            @Value("${flashstock.cognito.issuer}") String cognitoIssuer) {
        this.jwt=jwt; this.db=db; this.transaction=transaction; this.cognitoIssuer=cognitoIssuer;
    }

    public TokenPair exchange(Jwt cognito) {
        if (cognito == null || !cognitoIssuer.equals(cognito.getClaimAsString("iss"))
                || !"access".equals(cognito.getClaimAsString("token_use"))
                || cognito.getSubject()==null || cognito.getSubject().isBlank()) throw unauthorized();
        List<String> groups=cognito.getClaimAsStringList("cognito:groups");
        List<String> roles=new ArrayList<>();
        if (groups!=null) {
            if (groups.contains("USER") || groups.contains("ADMIN")) roles.add("ROLE_USER");
            if (groups.contains("ADMIN")) roles.add("ROLE_ADMIN");
        }
        if (!roles.contains("ROLE_USER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cognito USER or ADMIN group required");
        String username=cognito.getClaimAsString("username");
        if (username==null || username.isBlank()) username=cognito.getSubject();
        String subject=cognito.getSubject();
        String finalUsername=username;
        Instant expiry=Instant.now().plusSeconds(jwt.refreshSeconds()).truncatedTo(ChronoUnit.SECONDS);
        String family=UUID.randomUUID().toString();
        FlashstockJwtService.Issued issued=jwt.issue(subject,finalUsername,roles,family,expiry);
        TokenPair response=response(issued);
        transaction.executeWithoutResult(status -> {
            db.update("INSERT INTO flashstock_refresh_families(family_id,expires_at) VALUES (?,?)",family,expiry.toEpochMilli());
            insertSession(issued,subject,finalUsername,roles);
        });
        return response;
    }

    public TokenPair refresh(String raw) {
        Claims claims=parse(raw);
        Outcome result=transaction.execute(status -> rotate(raw,claims));
        if (result==null || result.status()!=Status.OK) throw unauthorized();
        return result.pair();
    }

    private Outcome rotate(String raw, Claims claims) {
        String familyId=claims.get("family_id",String.class);
        Family family=lockFamily(familyId);
        if (family==null || family.revoked()!=null) return new Outcome(Status.INVALID,null);
        Row row=lockSession(claims.getId());
        if (row==null) return new Outcome(Status.INVALID,null);
        long now=Instant.now().toEpochMilli();
        if (!row.family().equals(familyId) || !row.subject().equals(claims.getSubject())
                || !MessageDigest.isEqual(row.hash().getBytes(StandardCharsets.US_ASCII),sha256(raw).getBytes(StandardCharsets.US_ASCII))
                || row.used()!=null || row.revoked()!=null) {
            db.update("UPDATE flashstock_refresh_families SET revoked_at=? WHERE family_id=? AND revoked_at IS NULL",now,familyId);
            return new Outcome(Status.REUSED,null);
        }
        if (family.expiry()<=now || row.expires()<=now || row.expires()!=claims.getExpiration().getTime()) return new Outcome(Status.INVALID,null);
        List<String> roles=List.of(row.roles().split(","));
        if (!roles.contains("ROLE_USER") || roles.stream().anyMatch(r -> !r.equals("ROLE_USER") && !r.equals("ROLE_ADMIN"))) return new Outcome(Status.INVALID,null);
        db.update("UPDATE flashstock_refresh_sessions SET used_at=? WHERE jti=?",now,claims.getId());
        var issued=jwt.issue(row.subject(),row.username(),roles,familyId,Instant.ofEpochMilli(family.expiry()));
        insertSession(issued,row.subject(),row.username(),roles);
        return new Outcome(Status.OK,response(issued));
    }

    public void revoke(String raw) {
        Claims claims=parse(raw);
        Boolean changed=transaction.execute(status -> {
            String familyId=claims.get("family_id",String.class);
            Family family=lockFamily(familyId);
            Row row=lockSession(claims.getId());
            if (family==null || row==null || !row.family().equals(familyId) || !row.subject().equals(claims.getSubject())
                    || !MessageDigest.isEqual(row.hash().getBytes(StandardCharsets.US_ASCII),sha256(raw).getBytes(StandardCharsets.US_ASCII))) return false;
            db.update("UPDATE flashstock_refresh_families SET revoked_at=? WHERE family_id=? AND revoked_at IS NULL",Instant.now().toEpochMilli(),familyId);
            return true;
        });
        if (!Boolean.TRUE.equals(changed)) throw unauthorized();
    }

    private Claims parse(String token) {
        if (token==null || token.isBlank() || token.length()>8192) throw unauthorized();
        try { return jwt.verifyRefresh(token); } catch (JwtException | IllegalArgumentException ex) { throw unauthorized(); }
    }
    private Family lockFamily(String id) {
        var rows=db.query("SELECT expires_at,revoked_at FROM flashstock_refresh_families WHERE family_id=? FOR UPDATE",(rs,n)->new Family(rs.getLong(1),(Long)rs.getObject(2)),id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Row lockSession(String jti) {
        var rows=db.query("SELECT family_id,subject_id,username,roles,token_sha256,expires_at,used_at,revoked_at FROM flashstock_refresh_sessions WHERE jti=? FOR UPDATE",
                (rs,n)->new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),(Long)rs.getObject(7),(Long)rs.getObject(8)),jti);
        return rows.isEmpty()?null:rows.get(0);
    }
    private void insertSession(FlashstockJwtService.Issued token,String subject,String username,List<String> roles) {
        db.update("INSERT INTO flashstock_refresh_sessions (jti,family_id,subject_id,username,roles,token_sha256,expires_at) VALUES (?,?,?,?,?,?,?)",
                token.refreshJti(),token.familyId(),subject,username,String.join(",",roles),sha256(token.refreshToken()),token.refreshExpiresAt().toEpochMilli());
    }
    private TokenPair response(FlashstockJwtService.Issued issued) {
        return new TokenPair(issued.accessToken(),issued.refreshToken(),"Bearer",jwt.accessSeconds(),Math.max(0,issued.refreshExpiresAt().getEpochSecond()-Instant.now().getEpochSecond()));
    }
    private String sha256(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable",ex); }
    }
    private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid or reused refresh token"); }
}
