package com.bready.server.support;

import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

import com.bready.server.place.external.PlaceCatalogHttpClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc
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

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected PlaceCatalogHttpClient placeCatalogHttpClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @BeforeEach
    void givenFakePlaceCatalog() {
        FakePlaceCatalog fakePlaceCatalog = new FakePlaceCatalog();
        given(placeCatalogHttpClient.registerPlace(any()))
                .willAnswer(invocation -> fakePlaceCatalog.register(invocation.getArgument(0)));
    }

    @AfterEach
    void cleanUp() {
        truncateAllTables();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private void truncateAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables"
                        + " where table_schema = database() and table_type = 'BASE TABLE'",
                String.class);

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set foreign_key_checks = 0");
                for (String table : tables) {
                    statement.execute("truncate table `" + table + "`");
                }
                statement.execute("set foreign_key_checks = 1");
            }
            return null;
        });
    }
}
