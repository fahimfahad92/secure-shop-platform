package com.fahim.orderservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container shared by every integration test in the JVM.
 *
 * <p>Deliberately not using {@code @Testcontainers} + {@code @Container}: that lifecycle stops the
 * container after each test class and restarts it on a new random port for the next one, while
 * Spring reuses its cached context pointing at the old port. Starting it once here and letting Ryuk
 * reap it at JVM exit keeps the port stable across classes.
 *
 * <p>JWTs are verified against a test key from {@link TestJwts} instead of Keycloak, so a test that
 * sends a real signed token runs the same decoder and validators as production, including the
 * {@code audiences} check from application.properties. The issuer URI is blanked so Boot builds the
 * public-key decoder instead of one that would call Keycloak.
 */
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void jwtDecoder(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.public-key-location",
                TestJwts::publicKeyLocation);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
    }
}
