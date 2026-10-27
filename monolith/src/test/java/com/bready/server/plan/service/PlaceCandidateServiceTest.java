package com.bready.server.plan.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.plan.domain.CategoryState;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;
import com.bready.server.plan.dto.PlaceCandidateCreateResponse;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.exception.PlanErrorCase;
import com.bready.server.plan.fixture.PlaceCandidateFixture;
import com.bready.server.plan.repository.CategorySelectionLogRepository;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceCandidateServiceTest {

    private static final Long USER_ID = 1L;

    private static Plan ownedPlan() {
        return Plan.create(USER_ID, "제목", LocalDate.of(2026, 10, 20), "서울");
    }

    @InjectMocks
    private PlaceCandidateService placeCandidateService;

    @Mock
    private PlaceCandidateRepository placeCandidateRepository;

    @Mock
    private PlaceCatalogApi placeCatalogApi;

    @Mock
    private PlaceCandidateTransactionHandler placeCandidateTransactionHandler;

    @Mock
    private CategoryStateRepository categoryStateRepository;

    @Mock
    private CategorySelectionLogRepository categorySelectionLogRepository;

    @Test
    @DisplayName("성공 - 소유권 확인, Catalog 장소 등록, 후보 저장 순서로 부른다")
    void createCandidate_success() {
        PlaceCandidateCreateRequest request = PlaceCandidateFixture.createRequest();
        PlaceInfo place = PlaceCandidateFixture.registeredPlace();
        PlaceCandidateCreateResponse expected = new PlaceCandidateCreateResponse(100L, null, null);
        given(placeCatalogApi.register(any())).willReturn(place);
        given(placeCandidateTransactionHandler.create(USER_ID, request, place)).willReturn(expected);

        PlaceCandidateCreateResponse response = placeCandidateService.createCandidate(USER_ID, request);

        assertThat(response).isEqualTo(expected);
        InOrder inOrder = inOrder(placeCandidateTransactionHandler, placeCatalogApi);
        inOrder.verify(placeCandidateTransactionHandler).validateCreatable(USER_ID, request);
        inOrder.verify(placeCatalogApi).register(any());
        inOrder.verify(placeCandidateTransactionHandler).create(USER_ID, request, place);
    }

    @Test
    @DisplayName("실패 - 남의 플랜이면 Catalog에 장소를 등록하지 않고 PLAN_ACCESS_DENIED")
    void createCandidate_notOwner() {
        PlaceCandidateCreateRequest request = PlaceCandidateFixture.createRequest();
        willThrow(ApplicationException.from(PlanErrorCase.PLAN_ACCESS_DENIED))
                .given(placeCandidateTransactionHandler)
                .validateCreatable(USER_ID, request);

        assertThatThrownBy(() -> placeCandidateService.createCandidate(USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlanErrorCase.PLAN_ACCESS_DENIED);
        then(placeCatalogApi).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대표 장소 후보 최초 선택 성공")
    void setRepresentative_firstTime() {
        Long candidateId = 100L;

        PlanCategory category = mock(PlanCategory.class);
        given(category.getPlan()).willReturn(ownedPlan());
        when(category.getId()).thenReturn(10L);

        PlaceCandidate candidate = mock(PlaceCandidate.class);
        when(candidate.getCategory()).thenReturn(category);

        when(placeCandidateRepository.findByIdWithCategory(candidateId)).thenReturn(Optional.of(candidate));

        when(categoryStateRepository.findByCategory_IdForUpdate(10L)).thenReturn(Optional.empty());
        when(placeCandidateRepository.existsAliveByIdAndCategoryId(candidateId, 10L))
                .thenReturn(true);

        var response = placeCandidateService.setRepresentative(USER_ID, candidateId);

        assertThat(response.representativeCandidateId()).isEqualTo(candidateId);
        verify(categoryStateRepository).save(any());
        verify(categorySelectionLogRepository).save(any());
    }

    @Test
    @DisplayName("이미 대표 후보 → 예외")
    void setRepresentative_alreadyRepresentative() {
        Long candidateId = 100L;

        PlanCategory category = mock(PlanCategory.class);
        given(category.getPlan()).willReturn(ownedPlan());
        when(category.getId()).thenReturn(10L);

        PlaceCandidate candidate = mock(PlaceCandidate.class);
        when(candidate.getCategory()).thenReturn(category);

        CategoryState state = mock(CategoryState.class);
        when(state.isRepresentative(candidateId)).thenReturn(true);

        when(placeCandidateRepository.findByIdWithCategory(candidateId)).thenReturn(Optional.of(candidate));

        when(categoryStateRepository.findByCategory_IdForUpdate(10L)).thenReturn(Optional.of(state));
        when(placeCandidateRepository.existsAliveByIdAndCategoryId(candidateId, 10L))
                .thenReturn(true);

        assertThatThrownBy(() -> placeCandidateService.setRepresentative(USER_ID, candidateId))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.ALREADY_REPRESENTATIVE_CANDIDATE);
    }

    @Test
    @DisplayName("장소 후보 삭제 성공")
    void deleteCandidate_success() {
        Long candidateId = 100L;

        PlanCategory category = mock(PlanCategory.class);
        given(category.getPlan()).willReturn(ownedPlan());
        when(category.getId()).thenReturn(10L);

        PlaceCandidate candidate = mock(PlaceCandidate.class);
        when(candidate.getCategory()).thenReturn(category);

        when(placeCandidateRepository.findByIdWithCategory(candidateId)).thenReturn(Optional.of(candidate));

        when(categoryStateRepository.findByCategory_IdForUpdate(10L)).thenReturn(Optional.empty());

        var response = placeCandidateService.deleteCandidate(USER_ID, candidateId);

        assertThat(response.candidateId()).isEqualTo(candidateId);
        verify(candidate).softDelete();
    }
}
