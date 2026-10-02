
package com.shipping.config;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.*;

class AwsCognitoSecurityConfigTest {

    private static final String ISSUER =
            "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test";

    private static final String CLIENT = "flashstock-web-test";

    private Jwt token(
            String use,
            String client,
            String issuer,
            Instant expiry
    ) {

        Instant issuedAt = Instant.now().minusSeconds(360);

        return Jwt.withTokenValue("synthetic-non-network-test")
                .header("alg", "RS256")
                .subject("stable-user")
                .issuedAt(issuedAt)
                .expiresAt(expiry)
                .claim("iss", issuer)
                .claim("token_use", use)
                .claim("client_id", client)
                .build();
    }

    @Test
    void buildsDecoderWithoutNetworkValidation() {

        assertNotNull(
                new AwsCognitoSecurityConfig()
                        .cognitoDecoder(ISSUER, CLIENT)
        );
    }

    @Test
    void acceptsCorrectAccessTokenClaims() {

        Jwt jwt = token(
                "access",
                CLIENT,
                ISSUER,
                Instant.now().plusSeconds(300)
        );

        assertFalse(
                AwsCognitoSecurityConfig
                        .cognitoValidator(ISSUER, CLIENT)
                        .validate(jwt)
                        .hasErrors()
        );
    }

    @Test
    void rejectsIdToken() {

        Jwt jwt = token(
                "id",
                CLIENT,
                ISSUER,
                Instant.now().plusSeconds(300)
        );

        assertTrue(
                AwsCognitoSecurityConfig
                        .cognitoValidator(ISSUER, CLIENT)
                        .validate(jwt)
                        .hasErrors()
        );
    }

    @Test
    void rejectsOtherClient() {

        Jwt jwt = token(
                "access",
                "different-client",
                ISSUER,
                Instant.now().plusSeconds(300)
        );

        assertTrue(
                AwsCognitoSecurityConfig
                        .cognitoValidator(ISSUER, CLIENT)
                        .validate(jwt)
                        .hasErrors()
        );
    }

    @Test
    void rejectsOtherIssuer() {

        Jwt jwt = token(
                "access",
                CLIENT,
                "https://example.invalid/other",
                Instant.now().plusSeconds(300)
        );

        assertTrue(
                AwsCognitoSecurityConfig
                        .cognitoValidator(ISSUER, CLIENT)
                        .validate(jwt)
                        .hasErrors()
        );
    }

    @Test
    void rejectsExpiredToken() {

        Jwt jwt = token(
                "access",
                CLIENT,
                ISSUER,
                Instant.now().minusSeconds(180)
        );

        assertTrue(
                AwsCognitoSecurityConfig
                        .cognitoValidator(ISSUER, CLIENT)
                        .validate(jwt)
                        .hasErrors()
        );
    }
}
