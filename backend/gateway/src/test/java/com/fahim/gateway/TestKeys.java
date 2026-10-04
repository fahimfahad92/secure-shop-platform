package com.fahim.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Signs tokens the way the stub Keycloak would. Key pair generated per test JVM, never stored. */
final class TestKeys {

    private static final RSAKey KEY = generateKey();

    private TestKeys() {}

    static String jwkSetJson() {
        return new JWKSet(KEY.toPublicJWK()).toString();
    }

    /** Access token with Keycloak's realm_access claim. */
    static String accessToken(String subject, String... realmRoles) {
        return sign(
                baseClaims(subject)
                        .audience(List.of("order-service", "product-service", "user-service"))
                        .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                        .build());
    }

    /** ID token as Keycloak issues it to the given client: issuer, audience = client id. */
    static String idToken(String issuer, String clientId, String subject, String username) {
        return sign(
                baseClaims(subject)
                        .issuer(issuer)
                        .audience(clientId)
                        .claim("azp", clientId)
                        .claim("preferred_username", username)
                        .claim("email", username + "@example.com")
                        .build());
    }

    private static JWTClaimsSet.Builder baseClaims(String subject) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(5, ChronoUnit.MINUTES)));
    }

    private static String sign(JWTClaimsSet claims) {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                        claims);
        try {
            jwt.sign(new RSASSASigner(KEY));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
