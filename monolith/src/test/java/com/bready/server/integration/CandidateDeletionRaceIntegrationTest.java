package com.bready.server.integration;

import java.time.Duration;
import java.util.concurrent.Callable;
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

// 후보 삭제가 대표 후보를 바꾸는 요청(전환, 대표 지정)과 겹칠 때, 삭제된 후보가 대표로 남지 않는지 검증
@DisplayName("동시성 통합 테스트 - 후보 삭제와 대표 변경이 겹칠 때")
class CandidateDeletionRaceIntegrationTest extends IntegrationTestSupport {

    private static final int TO_CANDIDATE_NOT_FOUND = 7205;
    private static final int PLACE_CANDIDATE_NOT_FOUND = 4110;
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
    @DisplayName("실패 - 전환 대상 후보가 전환 도중 삭제되면 TO_CANDIDATE_NOT_FOUND이고 대표는 바뀌지 않는다")
    void executeSwitch_toCandidateDeletedConcurrently() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        long representativeId = steps.createCandidate(token, planId, categoryId, "kakao-1");
        long toCandidateId = steps.createCandidate(token, planId, categoryId, "kakao-2");
        long decisionId = steps.decideSwitch(token, steps.createTrigger(token, planId, categoryId));

        RaceResult result = raceAfterDeletion(
                categoryId,
                () -> steps.deleteCandidate(token, toCandidateId),
                () -> steps.switchTo(token, decisionId, toCandidateId));

        assertThat(result.deletion().getResponse().getStatus()).isEqualTo(200);
        assertThat(result.competitor().getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result.competitor())).isEqualTo(TO_CANDIDATE_NOT_FOUND);
        assertThat(currentRepresentativeId(categoryId)).isEqualTo(representativeId);
    }

    @Test
    @DisplayName("실패 - 대표로 지정하려는 후보가 지정 도중 삭제되면 PLACE_CANDIDATE_NOT_FOUND이고 대표는 바뀌지 않는다")
    void setRepresentative_candidateDeletedConcurrently() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        long representativeId = steps.createCandidate(token, planId, categoryId, "kakao-1");
        long candidateId = steps.createCandidate(token, planId, categoryId, "kakao-2");

        RaceResult result = raceAfterDeletion(
                categoryId,
                () -> steps.deleteCandidate(token, candidateId),
                () -> steps.setRepresentative(token, candidateId));

        assertThat(result.deletion().getResponse().getStatus()).isEqualTo(200);
        assertThat(result.competitor().getResponse().getStatus()).isEqualTo(404);
        assertThat(errorCode(result.competitor())).isEqualTo(PLACE_CANDIDATE_NOT_FOUND);
        assertThat(currentRepresentativeId(categoryId)).isEqualTo(representativeId);
    }

    // 삭제를 먼저 락 대기열에 세우고 그 뒤에 경쟁 요청을 세운 다음 락을 푼다. -> 삭제가 먼저 커밋
    private RaceResult raceAfterDeletion(long categoryId, Callable<MvcResult> deletion, Callable<MvcResult> competitor)
            throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> locker = executor.submit(() -> holdCategoryStateLock(categoryId, lockHeld, release));
        assertThat(lockHeld.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        Future<MvcResult> deleting = executor.submit(deletion);
        awaitLockWaiters(1);
        Future<MvcResult> competing = executor.submit(competitor);
        awaitLockWaiters(2);
        release.countDown();

        locker.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        return new RaceResult(
                deleting.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                competing.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
    }

    private void holdCategoryStateLock(long categoryId, CountDownLatch lockHeld, CountDownLatch release) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "select id from category_states where category_id = ? for update", Long.class, categoryId);
            lockHeld.countDown();
            try {
                release.await(LOCK_HOLD_LIMIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void awaitLockWaiters(int expected) {
        MySqlLockWaits.awaitWaiters(MYSQL, expected, TIMEOUT);
    }

    private long currentRepresentativeId(long categoryId) {
        return jdbcTemplate.queryForObject(
                "select current_candidate_id from category_states where category_id = ?", Long.class, categoryId);
    }

    private int errorCode(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.errorCode");
    }

    private record RaceResult(MvcResult deletion, MvcResult competitor) {}
}
