package com.bready.server.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static final int REDIS_PORT = 6379;

    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2").withExposedPorts(REDIS_PORT);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
