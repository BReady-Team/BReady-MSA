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
import static org.awaitility.Awaitility.await;

// 서로 다른 새 카테고리에 첫 후보가 동시에 들어와도 교착 없이 둘 다 대표가 정해지는지 검증
@DisplayName("동시성 통합 테스트 - 서로 다른 카테고리에 첫 후보를 동시에 추가할 때")
class FirstCandidateAcrossCategoriesRaceIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOCK_HOLD_LIMIT = Duration.ofSeconds(60);
    private static final long OTHER_CATEGORY_OFFSET = 1_000L;

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
    @DisplayName("성공 - 두 요청이 같은 인덱스 빈 구간에 갭 락을 쥘 수 있는 순서여도 둘 다 저장되고 각자 대표가 된다")
    void createCandidate_firstCandidatesOfDifferentCategoriesConcurrently() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long firstCategoryId = steps.createCategory(token, planId);
        long secondCategoryId = steps.createCategory(token, planId);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker = executor.submit(
                () -> holdMissingCategoryStateLock(secondCategoryId + OTHER_CATEGORY_OFFSET, lockHeld, release));
        assertThat(lockHeld.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        Future<MvcResult> first =
                executor.submit(() -> steps.createCandidateResult(token, planId, firstCategoryId, "kakao-1"));
        Future<MvcResult> second =
                executor.submit(() -> steps.createCandidateResult(token, planId, secondCategoryId, "kakao-2"));

        // 두 요청이 각자 끝났거나 갭 앞에서 기다릴 때까지. 교착은 해제 전에 둘 사이에서 이미 판정
        await().atMost(TIMEOUT).until(() -> doneCount(first, second) + MySqlLockWaits.countWaiters(MYSQL) == 2);
        release.countDown();

        locker.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult firstResult = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        MvcResult secondResult = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(representativeOf(firstCategoryId)).isEqualTo(candidateId(firstResult));
        assertThat(representativeOf(secondCategoryId)).isEqualTo(candidateId(secondResult));
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

    private int doneCount(Future<?>... requests) {
        int done = 0;
        for (Future<?> request : requests) {
            if (request.isDone()) {
                done++;
            }
        }
        return done;
    }

    private long representativeOf(long categoryId) {
        return jdbcTemplate.queryForObject(
                "select current_candidate_id from category_states where category_id = ?", Long.class, categoryId);
    }

    private long candidateId(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.candidateId");
        return value.longValue();
    }
}
