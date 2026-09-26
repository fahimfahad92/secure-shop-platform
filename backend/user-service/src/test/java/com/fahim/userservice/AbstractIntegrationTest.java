package com.fahim.userservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container shared by every integration test in the JVM.
 *
 * <p>Deliberately not using {@code @Testcontainers} + {@code @Container}: that lifecycle stops the
 * container after each test class and restarts it on a new random port for the next one, while
 * Spring reuses its cached context pointing at the old port. Starting it once here and letting Ryuk
 * reap it at JVM exit keeps the port stable across classes.
 *
 * <p>The service account secret is supplied via {@code @TestPropertySource} rather than a test
 * {@code application.properties}, which would shadow the main one by name and take the resource
 * server config down with it. Tests never reach a real Keycloak — {@code KeycloakAdminClient} is
 * mocked where it matters — but the property has to resolve for the context to start.
 */
@TestPropertySource(properties = "keycloak.admin.client-secret=test-secret")
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
