package com.fahim.productservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container shared by every integration test in the JVM.
 *
 * <p>Deliberately not using {@code @Testcontainers} + {@code @Container}: that lifecycle stops the
 * container after each test class and restarts it on a new random port for the next one, while
 * Spring reuses its cached context pointing at the old port. Starting it once here and letting Ryuk
 * reap it at JVM exit keeps the port stable across classes.
 */
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
