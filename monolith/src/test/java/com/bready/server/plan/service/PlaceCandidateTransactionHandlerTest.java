package com.bready.server.plan.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.plan.domain.CategorySelectionLog;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.PlaceSnapshot;
import com.bready.server.plan.dto.PlaceCandidateCreateResponse;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.exception.PlanErrorCase;
import com.bready.server.plan.fixture.PlaceCandidateFixture;
import com.bready.server.plan.repository.CategorySelectionLogRepository;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;
import com.bready.server.plan.repository.PlanCategoryRepository;

import static com.bready.server.plan.fixture.PlaceCandidateFixture.CATEGORY_ID;
import static com.bready.server.plan.fixture.PlaceCandidateFixture.PLAN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlaceCandidateTransactionHandlerTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long CANDIDATE_ID = 100L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private PlanCategoryRepository planCategoryRepository;

    @Mock
    private PlaceCandidateRepository placeCandidateRepository;

    @Mock
    private CategoryStateRepository categoryStateRepository;

    @Mock
    private CategorySelectionLogRepository categorySelectionLogRepository;

    private PlaceCandidateTransactionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlaceCandidateTransactionHandler(
                planCategoryRepository,
                placeCandidateRepository,
                categoryStateRepository,
                categorySelectionLogRepository,
                FIXED_CLOCK);
    }

    @Test
    @DisplayName("실패 - 남의 플랜이면 PLAN_ACCESS_DENIED")
    void validateCreatable_notOwner() {
        // given
        given(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(CATEGORY_ID, PLAN_ID))
                .willReturn(Optional.of(PlaceCandidateFixture.cafeCategoryOwnedBy(OWNER_ID)));

        // when, then
        assertThatThrownBy(() -> handler.validateCreatable(OTHER_USER_ID, PlaceCandidateFixture.createRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlanErrorCase.PLAN_ACCESS_DENIED);
    }

    @Test
    @DisplayName("실패 - 플랜에 없는 카테고리면 INVALID_PLAN_OR_CATEGORY")
    void validateCreatable_categoryNotInPlan() {
        // given
        given(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(CATEGORY_ID, PLAN_ID))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> handler.validateCreatable(OWNER_ID, PlaceCandidateFixture.createRequest()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.INVALID_PLAN_OR_CATEGORY);
    }

    @Test
    @DisplayName("성공 - 카테고리의 첫 후보면 대표로 지정하고 선택 기록을 Clock 시각으로 남긴다")
    void create_firstCandidateBecomesRepresentative() {
        // given
        givenOwnedCategory();
        given(placeCandidateRepository.saveAndFlush(any()))
                .willAnswer(invocation -> PlaceCandidateFixture.saved(invocation.getArgument(0), CANDIDATE_ID));
        given(categoryStateRepository.findByCategory_IdForUpdate(CATEGORY_ID)).willReturn(Optional.empty());

        // when
        PlaceCandidateCreateResponse response = handler.create(
                OWNER_ID, PlaceCandidateFixture.createRequest(), PlaceCandidateFixture.registeredPlace());

        // then
        assertThat(response.candidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(response.place().id()).isEqualTo(PlaceCandidateFixture.PLACE_ID);
        then(categoryStateRepository).should().saveAndFlush(any());
        ArgumentCaptor<CategorySelectionLog> log = ArgumentCaptor.forClass(CategorySelectionLog.class);
        then(categorySelectionLogRepository).should().save(log.capture());
        assertThat(log.getValue().getCandidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(log.getValue().getSelectedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 10, 0));
    }

    @Test
    @DisplayName("성공 - 저장하는 후보에 Catalog가 돌려준 장소를 복사한다")
    void create_copiesPlaceSnapshot() {
        // given
        givenOwnedCategory();
        given(placeCandidateRepository.saveAndFlush(any()))
                .willAnswer(invocation -> PlaceCandidateFixture.saved(invocation.getArgument(0), CANDIDATE_ID));
        given(categoryStateRepository.findByCategory_IdForUpdate(CATEGORY_ID)).willReturn(Optional.empty());

        // when
        handler.create(OWNER_ID, PlaceCandidateFixture.createRequest(), PlaceCandidateFixture.registeredPlace());

        // then
        ArgumentCaptor<PlaceCandidate> saved = ArgumentCaptor.forClass(PlaceCandidate.class);
        then(placeCandidateRepository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPlaceId()).isEqualTo(PlaceCandidateFixture.PLACE_ID);
        assertThat(saved.getValue().getPlaceSnapshot())
                .isEqualTo(new PlaceSnapshot(
                        PlaceCandidateFixture.EXTERNAL_ID,
                        PlaceCandidateFixture.NAME,
                        PlaceCandidateFixture.ADDRESS,
                        PlaceCandidateFixture.LATITUDE,
                        PlaceCandidateFixture.LONGITUDE,
                        true));
    }

    @Test
    @DisplayName("실패 - 같은 카테고리에 같은 장소가 이미 후보면 DUPLICATE_PLACE_CANDIDATE")
    void create_duplicate() {
        // given
        givenOwnedCategory();
        given(placeCandidateRepository.saveAndFlush(any())).willThrow(DataIntegrityViolationException.class);

        // when, then
        assertThatThrownBy(() -> handler.create(
                        OWNER_ID, PlaceCandidateFixture.createRequest(), PlaceCandidateFixture.registeredPlace()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.DUPLICATE_PLACE_CANDIDATE);
    }

    @Test
    @DisplayName("실패 - Catalog를 기다리는 사이 카테고리가 지워졌으면 INVALID_PLAN_OR_CATEGORY")
    void create_categoryDeletedWhileRegistering() {
        // given
        given(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(CATEGORY_ID, PLAN_ID))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> handler.create(
                        OWNER_ID, PlaceCandidateFixture.createRequest(), PlaceCandidateFixture.registeredPlace()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.INVALID_PLAN_OR_CATEGORY);
        then(placeCandidateRepository).shouldHaveNoInteractions();
    }

    private void givenOwnedCategory() {
        given(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(CATEGORY_ID, PLAN_ID))
                .willReturn(Optional.of(PlaceCandidateFixture.cafeCategoryOwnedBy(OWNER_ID)));
    }
}
