package com.bready.server.integration;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// Catalog 호출을 기다리는 동안 DB 커넥션을 쥐지 않는지 검증
@DisplayName("후보 추가 트랜잭션 경계 통합 테스트")
class CandidateCreationTransactionIntegrationTest extends IntegrationTestSupport {

    private static final PlaceInfo REGISTERED_PLACE = new PlaceInfo(
            1L, "kakao-1", "성수 카페", "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557"), true);

    @MockitoBean
    private PlaceCatalogApi placeCatalogApi;

    @Test
    @DisplayName("성공 - Catalog에 장소를 등록하는 동안에는 열린 트랜잭션이 없다")
    void createCandidate_registersPlaceOutsideTransaction() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        String token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);

        AtomicBoolean transactionActiveDuringRegister = new AtomicBoolean(true);
        given(placeCatalogApi.register(any())).willAnswer(invocation -> {
            transactionActiveDuringRegister.set(TransactionSynchronizationManager.isActualTransactionActive());
            return REGISTERED_PLACE;
        });

        steps.createCandidate(token, planId, categoryId, "kakao-1");

        assertThat(transactionActiveDuringRegister).isFalse();
    }
}
