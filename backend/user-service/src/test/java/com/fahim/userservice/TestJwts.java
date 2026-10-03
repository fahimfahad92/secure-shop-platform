package com.fahim.userservice;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mints real RS256-signed tokens for tests that need the full decode path.
 *
 * <p>{@code jwt()} from spring-security-test skips the {@code JwtDecoder}, so signature, expiry and
 * audience checks never run. Tests that assert on those send a token from here instead. The key
 * pair is generated once per JVM; only the public key is written to disk, as a temp file that Boot
 * loads through {@code public-key-location}. Nothing secret is committed.
 */
final class TestJwts {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private static final Path PUBLIC_KEY_FILE = writePublicKey();

    private TestJwts() {}

    /** Value for {@code spring.security.oauth2.resourceserver.jwt.public-key-location}. */
    static String publicKeyLocation() {
        return PUBLIC_KEY_FILE.toUri().toString();
    }

    /** A token shaped like Keycloak's: {@code aud}, space-separated {@code scope}, realm roles. */
    static String token(String audience, String scope, String... realmRoles) {
        Instant now = Instant.now();
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(UUID.randomUUID().toString())
                        .audience(audience)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                        .claim("scope", scope)
                        .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                        .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(KEY_PAIR.getPrivate()));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not sign test token", ex);
        }
        return jwt.serialize();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Path writePublicKey() {
        String pem =
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                                .encodeToString(KEY_PAIR.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n";
        try {
            Path file = Files.createTempFile("test-jwt-public", ".pem");
            Files.writeString(file, pem, StandardCharsets.US_ASCII);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
