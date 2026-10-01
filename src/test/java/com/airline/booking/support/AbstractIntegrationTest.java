package com.airline.booking.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test in this project.
 *
 * <p>The container is a {@code static} singleton started once for the whole test
 * suite. Deliberately NOT annotated {@code @Container} on an instance field:
 * that restarts PostgreSQL per test method and turns a seconds-long suite into a
 * minutes-long one.
 *
 * <p>PostgreSQL, not H2. The partial unique index and {@code INSERT ... ON
 * CONFLICT} that the booking design depends on do not exist in H2, so testing
 * against it would validate nothing that matters.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("airline")
                    .withUsername("airline")
                    .withPassword("airline");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
