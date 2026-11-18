package com.bready.server.integration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.bready.server.stats.domain.StatsPeriod;
import com.bready.server.stats.service.PlanStatsUpdater;
import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.MySqlLockWaits;
import com.bready.server.support.PlanningSteps;
import com.bready.server.trigger.api.ActivityQueryApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

// 늦게 끝난 통계 계산이 그사이 커밋된 전환을 모른 채 덮어쓰지 않는지 검증
@DisplayName("동시성 통합 테스트 - 통계 계산이 집계를 읽은 뒤 전환이 커밋될 때")
class PlanStatsStaleOverwriteRaceIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PAUSE_LIMIT = Duration.ofSeconds(60);

    @Autowired
    private PlanStatsUpdater planStatsUpdater;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ActivityQueryApi activityQueryApi;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("성공 - 집계를 읽고 멈춘 계산이 뒤늦게 끝나도 전환 1건이 통계에 남는다")
    void recalculate_pausedAfterReadingCountsWhileSwitchCommits() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        steps.createCandidate(token, planId, categoryId, "kakao-1");
        long secondCandidateId = steps.createCandidate(token, planId, categoryId, "kakao-2");
        long triggerId = steps.createTrigger(token, planId, categoryId);
        await().atMost(TIMEOUT).until(() -> periodsWithTriggerCounted(planId) == StatsPeriod.values().length);

        AtomicBoolean pauseNext = new AtomicBoolean(true);
        CountDownLatch countsRead = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        willAnswer(invocation -> {
                    Object switchCount = invocation.callRealMethod();
                    if (pauseNext.compareAndSet(true, false)) {
                        countsRead.countDown();
                        resume.await(PAUSE_LIMIT.toSeconds(), TimeUnit.SECONDS);
                    }
                    return switchCount;
                })
                .given(activityQueryApi)
                .countSwitchesOfPlan(any(), any());

        Future<?> paused = executor.submit(() -> planStatsUpdater.recalculate(planId, StatsPeriod.ALL));
        assertThat(countsRead.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        long decisionId = steps.decideSwitch(token, triggerId);
        steps.switchTo(token, decisionId, secondCandidateId);

        await().atMost(TIMEOUT).until(() -> switchesOf(planId) == 1 || MySqlLockWaits.countWaiters(MYSQL) == 1);
        resume.countDown();
        paused.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        await().atMost(TIMEOUT).until(() -> switchesOf(planId) == 1);
        assertThat(reliabilityOf(planId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private int periodsWithTriggerCounted(long planId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from plan_stats where plan_id = ? and total_triggers = 1", Integer.class, planId);
    }

    private int switchesOf(long planId) {
        return jdbcTemplate.queryForObject(
                "select total_switches from plan_stats where plan_id = ? and period = ?",
                Integer.class,
                planId,
                StatsPeriod.ALL.name());
    }

    private BigDecimal reliabilityOf(long planId) {
        return jdbcTemplate.queryForObject(
                "select reliability_score from plan_stats where plan_id = ? and period = ?",
                BigDecimal.class,
                planId,
                StatsPeriod.ALL.name());
    }
}
