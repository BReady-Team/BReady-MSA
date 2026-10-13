package com.bready.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Planning이 장소 정보를 얻는 방법을 바꿔도 응답 JSON은 그대로여야 하는것을 검증
@DisplayName("장소 정보 응답 계약 통합 테스트 - 후보 등록과 플랜 상세")
class PlaceCatalogContractIntegrationTest extends IntegrationTestSupport {

    private PlanningSteps steps;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        steps = new PlanningSteps(mockMvc);
        token = steps.signUpAndLogin("owner@example.com", "소유자");
    }

    @Test
    @DisplayName("성공 - 후보를 등록하면 응답에 장소의 id·외부 id·이름·주소·실내 여부가 담긴다")
    void createCandidate_returnsPlaceSummary() throws Exception {
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);

        MvcResult result = steps.createCandidateResult(token, planId, categoryId, "kakao-1");

        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(body, "$.data.candidateId")).isNotNull();
        assertThat(JsonPath.<Integer>read(body, "$.data.place.id")).isNotNull();
        assertThat(JsonPath.<String>read(body, "$.data.place.externalId")).isEqualTo("kakao-1");
        assertThat(JsonPath.<String>read(body, "$.data.place.name")).isEqualTo("kakao-1");
        assertThat(JsonPath.<String>read(body, "$.data.place.address")).isEqualTo("서울 성동구");
        assertThat(JsonPath.<Boolean>read(body, "$.data.place.isIndoor")).isTrue();
        assertThat(JsonPath.<String>read(body, "$.data.createdAt")).isNotBlank();
    }

    @Test
    @DisplayName("성공 - 같은 장소를 다른 플랜에 등록하면 장소는 새로 만들지 않고 같은 id를 돌려준다")
    void createCandidate_reusesPlace() throws Exception {
        long firstPlanId = steps.createPlan(token);
        long firstCategoryId = steps.createCategory(token, firstPlanId);
        long secondPlanId = steps.createPlan(token);
        long secondCategoryId = steps.createCategory(token, secondPlanId);

        MvcResult first = steps.createCandidateResult(token, firstPlanId, firstCategoryId, "kakao-1");
        MvcResult second = steps.createCandidateResult(token, secondPlanId, secondCategoryId, "kakao-1");

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(placeId(second)).isEqualTo(placeId(first));
    }

    @Test
    @DisplayName("성공 - 플랜 상세는 작성자 닉네임과 후보별 장소 정보를 최신 후보부터 돌려준다")
    void getPlanDetail_returnsOwnerAndPlaces() throws Exception {
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        MvcResult first = steps.createCandidateResult(token, planId, categoryId, "kakao-1");
        MvcResult second = steps.createCandidateResult(token, planId, categoryId, "kakao-2");
        long firstCandidateId = candidateId(first);
        long secondCandidateId = candidateId(second);

        mockMvc.perform(get("/api/v1/plans/{planId}", planId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.plan.planId").value(planId))
                .andExpect(jsonPath("$.data.plan.title").value("성수 나들이"))
                .andExpect(jsonPath("$.data.plan.planDate").value("2026-10-20"))
                .andExpect(jsonPath("$.data.plan.region").value("서울"))
                .andExpect(jsonPath("$.data.plan.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan.ownerNickname").value("소유자"))
                .andExpect(jsonPath("$.data.plan.ownerProfileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.categories.length()").value(1))
                .andExpect(jsonPath("$.data.categories[0].planCategoryId").value(categoryId))
                .andExpect(jsonPath("$.data.categories[0].categoryType").value("CAFE"))
                .andExpect(jsonPath("$.data.categories[0].sequence").value(1))
                .andExpect(jsonPath("$.data.categories[0].representativeCandidateId")
                        .value(firstCandidateId))
                .andExpect(jsonPath("$.data.categories[0].candidates.length()").value(2))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].candidateId")
                        .value(secondCandidateId))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].representative")
                        .value(false))
                .andExpect(
                        jsonPath("$.data.categories[0].candidates[0].place.id").value(placeId(second)))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.externalId")
                        .value("kakao-2"))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.name")
                        .value("kakao-2"))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.address")
                        .value("서울 성동구"))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.latitude")
                        .value(37.5445))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.longitude")
                        .value(127.0557))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.isIndoor")
                        .value(true))
                .andExpect(jsonPath("$.data.categories[0].candidates[1].candidateId")
                        .value(firstCandidateId))
                .andExpect(jsonPath("$.data.categories[0].candidates[1].representative")
                        .value(true))
                .andExpect(
                        jsonPath("$.data.categories[0].candidates[1].place.id").value(placeId(first)))
                .andExpect(jsonPath("$.data.categories[0].candidates[1].place.externalId")
                        .value("kakao-1"));
    }

    @Test
    @DisplayName("성공 - 후보가 없는 카테고리는 빈 후보 목록으로 나온다")
    void getPlanDetail_categoryWithoutCandidates() throws Exception {
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);

        mockMvc.perform(get("/api/v1/plans/{planId}", planId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].planCategoryId").value(categoryId))
                .andExpect(jsonPath("$.data.categories[0].representativeCandidateId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.categories[0].candidates.length()").value(0));
    }

    private long candidateId(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.candidateId");
        return value.longValue();
    }

    private long placeId(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.place.id");
        return value.longValue();
    }
}
