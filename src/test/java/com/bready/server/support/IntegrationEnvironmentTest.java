package com.bready.server.support;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// 통합 테스트가 H2나 로컬에 떠 있는 DB가 아니라 컨테이너에 붙었는지 확인
class IntegrationEnvironmentTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LettuceConnectionFactory redisConnectionFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("성공 - DataSource는 컨테이너의 MySQL 8.0에 연결")
    void dataSource_connectsToMySqlContainer() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        String database = jdbcTemplate.queryForObject("select database()", String.class);

        assertThat(version).startsWith("8.0");
        assertThat(database).isEqualTo(MYSQL.getDatabaseName());
    }

    @Test
    @DisplayName("성공 - 엔티티 테이블은 MySQL InnoDB로 만들어진다")
    void schema_isCreatedWithInnoDb() {
        String engine = jdbcTemplate.queryForObject(
                "select engine from information_schema.tables where table_schema = database() and table_name = 'plans'",
                String.class);

        assertThat(engine).isEqualTo("InnoDB");
    }

    @Test
    @DisplayName("성공 - Redis 연결은 컨테이너의 임의 포트를 가리키고 값을 저장·조회")
    void redis_connectsToRedisContainer() {
        String key = "integration-environment-test:ping";

        redisTemplate.opsForValue().set(key, "pong", Duration.ofSeconds(10));
        Object value = redisTemplate.opsForValue().get(key);

        assertThat(redisConnectionFactory.getPort()).isEqualTo(REDIS.getMappedPort(6379));
        assertThat(value).isEqualTo("pong");
    }
}
