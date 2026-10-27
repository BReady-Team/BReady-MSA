package com.bready.server.plan.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.place.api.PlaceRegisterRequest;
import com.bready.server.plan.domain.CategorySelectionLog;
import com.bready.server.plan.domain.CategoryState;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;
import com.bready.server.plan.dto.PlaceCandidateCreateResponse;
import com.bready.server.plan.dto.PlaceCandidateDeleteResponse;
import com.bready.server.plan.dto.PlaceCandidateRepresentativeResponse;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.repository.CategorySelectionLogRepository;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

    private final PlaceCandidateRepository placeCandidateRepository;
    private final PlaceCatalogApi placeCatalogApi;
    private final PlaceCandidateTransactionHandler placeCandidateTransactionHandler;
    private final CategoryStateRepository categoryStateRepository;
    private final CategorySelectionLogRepository categorySelectionLogRepository;

    // 트랜잭션 없음: Catalog에 장소를 등록하는 동안 DB 커넥션을 쥐지 않는다
    public PlaceCandidateCreateResponse createCandidate(Long userId, PlaceCandidateCreateRequest request) {
        placeCandidateTransactionHandler.validateCreatable(userId, request);

        // 장소가 먼저 커밋된다(중복이면 재사용). 뒤 단계가 실패하면 쓰이지 않는 장소만 남는다
        PlaceInfo place = placeCatalogApi.register(new PlaceRegisterRequest(
                request.externalId(),
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.isIndoor()));

        return placeCandidateTransactionHandler.create(userId, request, place);
    }

    // executeSwitch와 같은 이유로 READ_COMMITTED -> 락을 얻은 뒤의 조회가 최신 커밋을 봐야함
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlaceCandidateRepresentativeResponse setRepresentative(Long userId, Long candidateId) {

        PlaceCandidate candidate = placeCandidateRepository
                .findByIdWithCategory(candidateId)
                .orElseThrow(() -> ApplicationException.from(PlaceCandidateErrorCase.PLACE_CANDIDATE_NOT_FOUND));

        PlanCategory category = candidate.getCategory();
        category.getPlan().validateOwner(userId);
        Long categoryId = category.getId();

        CategoryState state =
                categoryStateRepository.findByCategory_IdForUpdate(categoryId).orElse(null);

        // 락을 기다리는 동안 후보가 삭제됐을 수 있기에, 위에서 읽은 candidate는 락 이전의 모습이라 다시 확인
        if (!placeCandidateRepository.existsAliveByIdAndCategoryId(candidateId, categoryId)) {
            throw ApplicationException.from(PlaceCandidateErrorCase.PLACE_CANDIDATE_NOT_FOUND);
        }

        if (state == null) {
            // 첫 대표 지정
            state = CategoryState.create(category, candidateId);
            categoryStateRepository.save(state);
        } else {
            // 이미 대표인 경우
            if (state.isRepresentative(candidateId)) {
                throw ApplicationException.from(PlaceCandidateErrorCase.ALREADY_REPRESENTATIVE_CANDIDATE);
            }
            state.changeRepresentative(candidateId);
        }

        saveSelectionLog(categoryId, candidateId);

        return PlaceCandidateRepresentativeResponse.builder()
                .categoryId(categoryId)
                .representativeCandidateId(candidateId)
                .changedAt(state.getUpdatedAt())
                .build();
    }

    @Transactional
    public PlaceCandidateDeleteResponse deleteCandidate(Long userId, Long candidateId) {
        PlaceCandidate candidate = placeCandidateRepository
                .findByIdWithCategory(candidateId)
                .orElseThrow(() -> ApplicationException.from(PlaceCandidateErrorCase.PLACE_CANDIDATE_NOT_FOUND));

        candidate.getCategory().getPlan().validateOwner(userId);
        Long categoryId = candidate.getCategory().getId();

        CategoryState state =
                categoryStateRepository.findByCategory_IdForUpdate(categoryId).orElse(null);

        if (state != null
                && state.getCurrentCandidateId() != null
                && state.getCurrentCandidateId().equals(candidateId)) {
            throw ApplicationException.from(PlaceCandidateErrorCase.REPRESENTATIVE_CANDIDATE_CANNOT_DELETE);
        }

        candidate.softDelete();

        return PlaceCandidateDeleteResponse.builder()
                .candidateId(candidateId)
                .deletedAt(candidate.getDeletedAt())
                .build();
    }

    private void saveSelectionLog(Long categoryId, Long candidateId) {
        categorySelectionLogRepository.save(CategorySelectionLog.of(categoryId, candidateId, LocalDateTime.now()));
    }
}
