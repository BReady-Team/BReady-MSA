package com.bready.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.ResourceAccessException;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Planning이 장소 정보를 얻는 방법을 바꿔도 응답 JSON은 그대로여야 하는것을 검증
@DisplayName("장소 정보 응답 계약 통합 테스트 - 후보 등록과 플랜 상세")
class PlaceCatalogContractIntegrationTest extends IntegrationTestSupport {

    private static final int PLACE_CATALOG_UNAVAILABLE = 5031;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("실패 - Catalog에 연결하지 못하면 503 PLACE_CATALOG_UNAVAILABLE이고 후보도 생기지 않는다")
    void createCandidate_catalogUnavailable() throws Exception {
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        willThrow(new ResourceAccessException("Connection refused"))
                .given(placeCatalogHttpClient)
                .registerPlace(any());

        MvcResult result = steps.createCandidateResult(token, planId, categoryId, "kakao-1");

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(JsonPath.<Integer>read(result.getResponse().getContentAsString(), "$.errorCode"))
                .isEqualTo(PLACE_CATALOG_UNAVAILABLE);
        assertThat(jdbcTemplate.queryForObject("select count(*) from place_candidates", Long.class))
                .isZero();
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
    @DisplayName("성공 - 플랜 상세는 Catalog를 부르지 않고 후보의 스냅샷으로 장소 정보를 돌려준다")
    void getPlanDetail_readsSnapshotWithoutCatalog() throws Exception {
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        MvcResult created = steps.createCandidateResult(token, planId, categoryId, "kakao-1");
        clearInvocations(placeCatalogHttpClient);

        mockMvc.perform(get("/api/v1/plans/{planId}", planId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.categories[0].candidates[0].place.id").value(placeId(created)))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.externalId")
                        .value("kakao-1"))
                .andExpect(jsonPath("$.data.categories[0].candidates[0].place.latitude")
                        .value(37.5445));

        then(placeCatalogHttpClient).shouldHaveNoInteractions();
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
