package com.bready.server.plan.service;

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
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;
import com.bready.server.plan.dto.PlaceCandidateCreateResponse;
import com.bready.server.plan.dto.PlaceCandidateDeleteResponse;
import com.bready.server.plan.dto.PlaceCandidateRepresentativeResponse;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.exception.PlanErrorCase;
import com.bready.server.plan.fixture.PlaceCandidateFixture;
import com.bready.server.plan.repository.CategorySelectionLogRepository;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;

import static com.bready.server.plan.fixture.PlaceCandidateFixture.CATEGORY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PlaceCandidateServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CANDIDATE_ID = 100L;
    private static final Long OTHER_CANDIDATE_ID = 99L;

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
    @DisplayName("성공 - 대표가 없는 카테고리에서 후보를 대표로 지정하고 선택 기록을 남긴다")
    void setRepresentative_noRepresentativeYet() {
        PlanCategory category = PlaceCandidateFixture.cafeCategoryOwnedBy(USER_ID);
        CategoryState state = PlaceCandidateFixture.emptyState(category);
        givenCandidate(category);
        given(categoryStateRepository.getByCategoryIdForUpdate(CATEGORY_ID)).willReturn(state);
        given(placeCandidateRepository.existsAliveByIdAndCategoryId(CANDIDATE_ID, CATEGORY_ID))
                .willReturn(true);

        PlaceCandidateRepresentativeResponse response = placeCandidateService.setRepresentative(USER_ID, CANDIDATE_ID);

        assertThat(response.representativeCandidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(state.getCurrentCandidateId()).isEqualTo(CANDIDATE_ID);
        then(categorySelectionLogRepository).should().save(any());
    }

    @Test
    @DisplayName("실패 - 이미 대표인 후보를 다시 지정하면 ALREADY_REPRESENTATIVE_CANDIDATE")
    void setRepresentative_alreadyRepresentative() {
        PlanCategory category = PlaceCandidateFixture.cafeCategoryOwnedBy(USER_ID);
        givenCandidate(category);
        given(categoryStateRepository.getByCategoryIdForUpdate(CATEGORY_ID))
                .willReturn(PlaceCandidateFixture.stateRepresenting(category, CANDIDATE_ID));
        given(placeCandidateRepository.existsAliveByIdAndCategoryId(CANDIDATE_ID, CATEGORY_ID))
                .willReturn(true);

        assertThatThrownBy(() -> placeCandidateService.setRepresentative(USER_ID, CANDIDATE_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.ALREADY_REPRESENTATIVE_CANDIDATE);
    }

    @Test
    @DisplayName("성공 - 대표가 아닌 후보를 지우면 soft delete 된다")
    void deleteCandidate_notRepresentative() {
        PlanCategory category = PlaceCandidateFixture.cafeCategoryOwnedBy(USER_ID);
        PlaceCandidate candidate = givenCandidate(category);
        given(categoryStateRepository.getByCategoryIdForUpdate(CATEGORY_ID))
                .willReturn(PlaceCandidateFixture.stateRepresenting(category, OTHER_CANDIDATE_ID));

        PlaceCandidateDeleteResponse response = placeCandidateService.deleteCandidate(USER_ID, CANDIDATE_ID);

        assertThat(response.candidateId()).isEqualTo(CANDIDATE_ID);
        assertThat(candidate.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패 - 대표 후보를 지우면 REPRESENTATIVE_CANDIDATE_CANNOT_DELETE")
    void deleteCandidate_representative() {
        PlanCategory category = PlaceCandidateFixture.cafeCategoryOwnedBy(USER_ID);
        PlaceCandidate candidate = givenCandidate(category);
        given(categoryStateRepository.getByCategoryIdForUpdate(CATEGORY_ID))
                .willReturn(PlaceCandidateFixture.stateRepresenting(category, CANDIDATE_ID));

        assertThatThrownBy(() -> placeCandidateService.deleteCandidate(USER_ID, CANDIDATE_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceCandidateErrorCase.REPRESENTATIVE_CANDIDATE_CANNOT_DELETE);
        assertThat(candidate.getDeletedAt()).isNull();
    }

    private PlaceCandidate givenCandidate(PlanCategory category) {
        PlaceCandidate candidate = PlaceCandidateFixture.candidateOf(category, CANDIDATE_ID);
        given(placeCandidateRepository.findByIdWithCategory(CANDIDATE_ID)).willReturn(Optional.of(candidate));
        return candidate;
    }
}
