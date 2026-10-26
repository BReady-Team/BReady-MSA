package com.bready.server.plan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.plan.domain.CategoryState;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.PlaceSnapshot;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.repository.CategorySelectionLogRepository;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;
import com.bready.server.plan.repository.PlanCategoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceCandidateServiceTest {

    private static final Long USER_ID = 1L;

    private static final Long PLACE_ID = 5L;

    private static Plan ownedPlan() {
        return Plan.create(USER_ID, "제목", LocalDate.of(2026, 10, 20), "서울");
    }

    private static PlaceInfo registeredPlace() {
        return new PlaceInfo(
                PLACE_ID, "kakao-123", "성수 카페", "서울 성동구", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), true);
    }

    @InjectMocks
    private PlaceCandidateService placeCandidateService;

    @Mock
    private PlanCategoryRepository planCategoryRepository;

    @Mock
    private PlaceCandidateRepository placeCandidateRepository;

    @Mock
    private PlaceCatalogApi placeCatalogApi;

    @Mock
    private CategoryStateRepository categoryStateRepository;

    @Mock
    private CategorySelectionLogRepository categorySelectionLogRepository;

    @Test
    @DisplayName("장소 후보 등록 성공")
    void createCandidate_success() {
        PlaceCandidateCreateRequest request = new PlaceCandidateCreateRequest(
                1L, 10L, "kakao-123", "성수 카페", "서울 성동구", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), true);

        PlanCategory category = mock(PlanCategory.class);
        given(category.getPlan()).willReturn(ownedPlan());
        PlaceInfo place = registeredPlace();

        PlaceCandidate savedCandidate = mock(PlaceCandidate.class);
        when(savedCandidate.getId()).thenReturn(100L);
        when(savedCandidate.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(10L, 1L))
                .thenReturn(Optional.of(category));

        given(placeCatalogApi.register(any())).willReturn(place);

        when(placeCandidateRepository.saveAndFlush(any())).thenReturn(savedCandidate);

        var response = placeCandidateService.createCandidate(USER_ID, request);

        assertThat(response.candidateId()).isEqualTo(100L);
        assertThat(response.place().id()).isEqualTo(PLACE_ID);
        assertThat(response.place().name()).isEqualTo("성수 카페");
        verify(placeCandidateRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("성공 - 저장하는 후보에 Catalog가 돌려준 장소를 복사한다")
    void createCandidate_copiesPlaceSnapshot() {
        PlaceCandidateCreateRequest request = new PlaceCandidateCreateRequest(
                1L, 10L, "kakao-123", "성수 카페", "서울 성동구", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), true);
        PlanCategory category = PlanCategory.create(ownedPlan(), PlaceCategoryType.CAFE, 1);
        given(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(10L, 1L))
                .willReturn(Optional.of(category));
        given(placeCatalogApi.register(any())).willReturn(registeredPlace());
        given(placeCandidateRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        placeCandidateService.createCandidate(USER_ID, request);

        ArgumentCaptor<PlaceCandidate> saved = ArgumentCaptor.forClass(PlaceCandidate.class);
        then(placeCandidateRepository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPlaceId()).isEqualTo(PLACE_ID);
        assertThat(saved.getValue().getPlaceSnapshot())
                .isEqualTo(new PlaceSnapshot(
                        "kakao-123", "성수 카페", "서울 성동구", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), true));
    }

    @Test
    @DisplayName("장소 후보 중복 등록 → DUPLICATE_PLACE_CANDIDATE")
    void createCandidate_duplicate() {
        PlaceCandidateCreateRequest request = new PlaceCandidateCreateRequest(
                1L, 10L, "kakao-123", "성수 카페", "서울 성동구", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), true);

        PlanCategory category = mock(PlanCategory.class);
        given(category.getPlan()).willReturn(ownedPlan());
        PlaceInfo place = registeredPlace();

        when(planCategoryRepository.findByIdAndPlan_IdAndDeletedAtIsNull(10L, 1L))
                .thenReturn(Optional.of(category));

        given(placeCatalogApi.register(any())).willReturn(place);

        when(placeCandidateRepository.saveAndFlush(any())).thenThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> placeCandidateService.createCandidate(USER_ID, request))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.DUPLICATE_PLACE_CANDIDATE);
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
