package com.bready.server.integration;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bready.server.place.external.PlaceCatalogResponse;
import com.bready.server.recommendation.port.CategoryRecommendationPort;
import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 추천이 외부 API를 기다리는 동안 DB 커넥션을 쥐지 않는지 검증
@DisplayName("추천 트랜잭션 경계 통합 테스트")
class RecommendationTransactionBoundaryIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    private CategoryRecommendationPort categoryRecommendationPort;

    private String token;
    private long triggerId;

    @BeforeEach
    void setUp() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId, "CAFE");
        steps.createCandidate(token, planId, categoryId, "kakao-1");
        triggerId = steps.createTrigger(token, planId, categoryId);
    }

    @Test
    @DisplayName("성공 - 장소 추천이 Catalog를 검색하는 동안에는 열린 트랜잭션이 없다")
    void recommendPlaces_searchesCatalogOutsideTransaction() throws Exception {
        AtomicBoolean transactionActiveDuringSearch = new AtomicBoolean(true);
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    transactionActiveDuringSearch.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return new PlaceCatalogResponse<>(null, "success", List.of());
                });

        recommend("/api/v1/recommendations/places");

        assertThat(transactionActiveDuringSearch).isFalse();
    }

    @Test
    @DisplayName("성공 - 카테고리 추천이 추천 포트를 부르는 동안에는 열린 트랜잭션이 없다")
    void recommendCategories_callsPortOutsideTransaction() throws Exception {
        AtomicBoolean transactionActiveDuringRecommend = new AtomicBoolean(true);
        given(categoryRecommendationPort.recommendCategories(any(), any(), any()))
                .willAnswer(invocation -> {
                    transactionActiveDuringRecommend.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return List.of();
                });

        recommend("/api/v1/recommendations/categories");

        assertThat(transactionActiveDuringRecommend).isFalse();
    }

    private void recommend(String path) throws Exception {
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"triggerId": %d}
                                """
                                        .formatted(triggerId)))
                .andExpect(status().isOk());
    }
}
