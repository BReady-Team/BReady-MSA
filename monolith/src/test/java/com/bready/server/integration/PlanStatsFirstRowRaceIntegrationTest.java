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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bready.server.stats.domain.StatsPeriod;
import com.bready.server.stats.service.PlanStatsUpdater;
import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.MySqlLockWaits;
import com.bready.server.support.PlanningSteps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

// 서로 다른 플랜의 첫 통계 계산이 동시에 일어나도 교착 없이 둘 다 저장되는지 검증
@DisplayName("동시성 통합 테스트 - 서로 다른 플랜의 첫 통계를 동시에 계산할 때")
class PlanStatsFirstRowRaceIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOCK_HOLD_LIMIT = Duration.ofSeconds(60);
    private static final long OTHER_PLAN_OFFSET = 1_000L;

    @Autowired
    private PlanStatsUpdater planStatsUpdater;

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
    @DisplayName("성공 - 두 계산이 같은 인덱스 빈 구간에 갭 락을 쥘 수 있는 순서여도 둘 다 통계 행을 만든다")
    void recalculate_firstStatsOfDifferentPlansConcurrently() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long firstPlanId = steps.createPlan(token);
        long secondPlanId = steps.createPlan(token);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker =
                executor.submit(() -> holdMissingStatsLock(secondPlanId + OTHER_PLAN_OFFSET, lockHeld, release));
        assertThat(lockHeld.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        Future<?> first = executor.submit(() -> planStatsUpdater.recalculate(firstPlanId, StatsPeriod.ALL));
        Future<?> second = executor.submit(() -> planStatsUpdater.recalculate(secondPlanId, StatsPeriod.ALL));

        // 두 계산이 각자 끝났거나 갭 앞에서 기다릴 때까지, 교착은 해제 전에 둘 사이에서 판정
        await().atMost(TIMEOUT).until(() -> doneCount(first, second) + MySqlLockWaits.countWaiters(MYSQL) == 2);
        release.countDown();
        locker.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThatCode(() -> first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThatCode(() -> second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThat(statsRowCount(firstPlanId)).isEqualTo(1);
        assertThat(statsRowCount(secondPlanId)).isEqualTo(1);
    }

    private void holdMissingStatsLock(long planId, CountDownLatch lockHeld, CountDownLatch release) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForList(
                    "select id from plan_stats where plan_id = ? and period = ? for update",
                    Long.class,
                    planId,
                    StatsPeriod.ALL.name());
            lockHeld.countDown();
            try {
                release.await(LOCK_HOLD_LIMIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private int doneCount(Future<?>... tasks) {
        int done = 0;
        for (Future<?> task : tasks) {
            if (task.isDone()) {
                done++;
            }
        }
        return done;
    }

    private long statsRowCount(long planId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from plan_stats where plan_id = ? and period = ?",
                Long.class,
                planId,
                StatsPeriod.ALL.name());
    }
}
