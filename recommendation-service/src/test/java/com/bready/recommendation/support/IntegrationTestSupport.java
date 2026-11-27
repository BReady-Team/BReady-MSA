package com.bready.recommendation.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.GenericContainer;

import com.bready.recommendation.catalog.external.PlaceCatalogHttpClient;
import com.bready.recommendation.planning.external.PlanningHttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected PlanningHttpClient planningHttpClient;

    @MockitoBean
    protected PlaceCatalogHttpClient placeCatalogHttpClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void flushCache() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    protected static HttpClientErrorException errorResponse(HttpStatus status, String json) {
        HttpClientErrorException exception = HttpClientErrorException.create(
                status,
                status.getReasonPhrase(),
                HttpHeaders.EMPTY,
                json.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        exception.setBodyConvertFunction(type -> {
            try {
                return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.constructType(type.getType()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        return exception;
    }

    protected static HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }
}
