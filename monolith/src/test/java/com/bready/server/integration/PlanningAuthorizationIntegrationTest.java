package com.bready.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;

// 로그인한 다른 사용자가 남의 플랜을 바꾸지 못하는지 검증
@DisplayName("권한 통합 테스트 - 남의 플랜은 바꿀 수 없다")
class PlanningAuthorizationIntegrationTest extends IntegrationTestSupport {

    private static final int PLAN_ACCESS_DENIED = 4002;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PlanningSteps steps;
    private String ownerToken;
    private String otherToken;
    private long planId;
    private long categoryId;
    private long representativeId;
    private long candidateId;
    private long triggerId;
    private long decisionId;

    @BeforeEach
    void setUpOwnersPlan() throws Exception {
        steps = new PlanningSteps(mockMvc);
        ownerToken = steps.signUpAndLogin("owner@example.com", "소유자");
        otherToken = steps.signUpAndLogin("other@example.com", "다른사람");
        planId = steps.createPlan(ownerToken);
        categoryId = steps.createCategory(ownerToken, planId);
        representativeId = steps.createCandidate(ownerToken, planId, categoryId, "kakao-1");
        candidateId = steps.createCandidate(ownerToken, planId, categoryId, "kakao-2");
        triggerId = steps.createTrigger(ownerToken, planId, categoryId);
        decisionId = steps.decideSwitch(ownerToken, triggerId);
    }

    @Test
    @DisplayName("실패 - 남의 플랜에 후보를 등록하면 PLAN_ACCESS_DENIED")
    void createCandidate_notOwner() throws Exception {
        MvcResult result = steps.createCandidateResult(otherToken, planId, categoryId, "kakao-3");

        assertAccessDenied(result);
        assertThat(count("place_candidates")).isEqualTo(2);
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 대표 후보를 바꾸면 PLAN_ACCESS_DENIED")
    void setRepresentative_notOwner() throws Exception {
        MvcResult result = steps.setRepresentative(otherToken, candidateId);

        assertAccessDenied(result);
        assertThat(currentRepresentativeId()).isEqualTo(representativeId);
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 후보를 삭제하면 PLAN_ACCESS_DENIED")
    void deleteCandidate_notOwner() throws Exception {
        MvcResult result = steps.deleteCandidate(otherToken, candidateId);

        assertAccessDenied(result);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from place_candidates where id = ? and deleted_at is null",
                        Integer.class,
                        candidateId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("실패 - 남의 플랜에 트리거를 만들면 PLAN_ACCESS_DENIED")
    void createTrigger_notOwner() throws Exception {
        MvcResult result = steps.createTriggerResult(otherToken, planId, categoryId);

        assertAccessDenied(result);
        assertThat(count("triggers")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 트리거에 결정을 내리면 PLAN_ACCESS_DENIED")
    void createDecision_notOwner() throws Exception {
        long undecidedTriggerId = steps.createTrigger(ownerToken, planId, categoryId);

        MvcResult result = steps.decideSwitchResult(otherToken, undecidedTriggerId);

        assertAccessDenied(result);
        assertThat(count("decisions")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 - 남의 플랜에서 전환을 실행하면 PLAN_ACCESS_DENIED")
    void executeSwitch_notOwner() throws Exception {
        MvcResult result = steps.switchTo(otherToken, decisionId, candidateId);

        assertAccessDenied(result);
        assertThat(currentRepresentativeId()).isEqualTo(representativeId);
        assertThat(count("switch_logs")).isZero();
    }

    private void assertAccessDenied(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        int errorCode = JsonPath.read(result.getResponse().getContentAsString(), "$.errorCode");
        assertThat(errorCode).isEqualTo(PLAN_ACCESS_DENIED);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private long currentRepresentativeId() {
        return jdbcTemplate.queryForObject(
                "select current_candidate_id from category_states where category_id = ?", Long.class, categoryId);
    }
}
