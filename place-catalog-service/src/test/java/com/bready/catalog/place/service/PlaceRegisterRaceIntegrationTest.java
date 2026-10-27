package com.bready.catalog.place.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bready.catalog.place.dto.PlaceRegisterRequest;
import com.bready.catalog.place.dto.PlaceRegisterResponse;
import com.bready.catalog.support.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// 같은 장소를 두 요청이 동시에 등록할 때, 늦은 쪽이 먼저 저장된 장소를 돌려받는지 검증
@DisplayName("동시성 통합 테스트 - 같은 외부 ID 장소를 동시에 등록할 때")
class PlaceRegisterRaceIntegrationTest extends IntegrationTestSupport {

    private static final String EXTERNAL_ID = "kakao-race";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    PlaceRegisterService placeRegisterService;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("성공 - 다른 요청이 먼저 저장하면 중복 저장에 실패한 뒤 그 장소를 다시 조회해 돌려준다")
    void register_concurrentInsert() throws Exception {
        // 다른 요청이 같은 외부 ID를 INSERT 하고 아직 커밋하지 않은 상태
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        Future<?> other = executor.submit(() -> insertAndHoldUntilCommit(inserted, commit));
        assertThat(inserted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        // 이 요청은 조회에서 못 찾고 INSERT 하다가 유니크 인덱스에서 기다리고, 그때 상대가 커밋
        Future<PlaceRegisterResponse> registering = executor.submit(() -> placeRegisterService.register(request()));
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(countLockWaiters()).isEqualTo(1));
        commit.countDown();
        other.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        PlaceRegisterResponse response = registering.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(response.name()).isEqualTo("먼저 저장된 카페");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from places where external_id = ?", Integer.class, EXTERNAL_ID))
                .isEqualTo(1);
    }

    private void insertAndHoldUntilCommit(CountDownLatch inserted, CountDownLatch commit) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "insert into places (external_id, name, latitude, longitude, created_at, updated_at)"
                            + " values (?, '먼저 저장된 카페', 37.5445, 127.0557, now(), now())",
                    EXTERNAL_ID);
            inserted.countDown();
            try {
                commit.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private int countLockWaiters() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select count(distinct requesting_engine_transaction_id)"
                        + " from performance_schema.data_lock_waits")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private PlaceRegisterRequest request() {
        return new PlaceRegisterRequest(
                EXTERNAL_ID, "늦게 저장하려던 카페", "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557"), true);
    }
}
