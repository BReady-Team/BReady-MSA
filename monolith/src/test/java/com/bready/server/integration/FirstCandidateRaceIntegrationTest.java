package com.bready.server.integration;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.MySqlLockWaits;
import com.bready.server.support.PlanningSteps;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 카테고리에 첫 후보 두 개가 동시에 들어와도 대표 상태가 하나만 생기고 둘 다 성공하는지 검증
@DisplayName("동시성 통합 테스트 - 같은 카테고리에 첫 후보를 동시에 추가할 때")
class FirstCandidateRaceIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOCK_HOLD_LIMIT = Duration.ofSeconds(60);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("성공 - 두 요청이 모두 대표 상태가 없다고 볼 수 있는 순서여도 둘 다 저장되고 먼저 온 후보가 대표가 된다")
    void createCandidate_firstCandidatesConcurrently() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);

        // 아직 없는 대표 상태 행을 잠가 두 요청이 상태를 만들기 전에 멈추게 한다
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker = executor.submit(() -> holdMissingCategoryStateLock(categoryId, lockHeld, release));
        assertThat(lockHeld.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        Future<MvcResult> first =
                executor.submit(() -> steps.createCandidateResult(token, planId, categoryId, "kakao-1"));
        MySqlLockWaits.awaitWaiters(MYSQL, 1, TIMEOUT);
        Future<MvcResult> second =
                executor.submit(() -> steps.createCandidateResult(token, planId, categoryId, "kakao-2"));
        MySqlLockWaits.awaitWaiters(MYSQL, 2, TIMEOUT);
        release.countDown();

        locker.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult firstResult = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult secondResult = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(queryLong("select count(*) from place_candidates where category_id = ?", categoryId))
                .isEqualTo(2);
        assertThat(queryLong("select count(*) from category_states where category_id = ?", categoryId))
                .isEqualTo(1);
        assertThat(queryLong("select current_candidate_id from category_states where category_id = ?", categoryId))
                .isEqualTo(candidateId(firstResult));
    }

    private void holdMissingCategoryStateLock(long categoryId, CountDownLatch lockHeld, CountDownLatch release) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForList(
                    "select id from category_states where category_id = ? for update", Long.class, categoryId);
            lockHeld.countDown();
            try {
                release.await(LOCK_HOLD_LIMIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private long queryLong(String sql, long categoryId) {
        return jdbcTemplate.queryForObject(sql, Long.class, categoryId);
    }

    private long candidateId(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.candidateId");
        return value.longValue();
    }
}
