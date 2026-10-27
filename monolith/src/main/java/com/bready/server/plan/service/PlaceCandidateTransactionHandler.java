package com.bready.server.plan.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.plan.domain.CategorySelectionLog;
import com.bready.server.plan.domain.CategoryState;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.PlaceSnapshot;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.PlaceCandidateCreateRequest;
import com.bready.server.plan.dto.PlaceCandidateCreateResponse;
import com.bready.server.plan.dto.PlaceSummaryResponse;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.repository.CategorySelectionLogRepository;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;
import com.bready.server.plan.repository.PlanCategoryRepository;

import lombok.RequiredArgsConstructor;

// 후보 추가의 DB 작업만 맡는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceCandidateTransactionHandler {

    private final PlanCategoryRepository planCategoryRepository;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final CategoryStateRepository categoryStateRepository;
    private final CategorySelectionLogRepository categorySelectionLogRepository;
    private final Clock clock;

    public void validateCreatable(Long userId, PlaceCandidateCreateRequest request) {
        getOwnedCategory(userId, request);
    }

    // Catalog를 기다리는 사이 카테고리가 지워졌을 수 있어 다시 확인
    @Transactional
    public PlaceCandidateCreateResponse create(Long userId, PlaceCandidateCreateRequest request, PlaceInfo place) {
        // 카테고리 행을 잠가 같은 카테고리의 후보 추가를 줄 세운다 -> 첫 후보 동시 추가 시 대표 상태 중복 생성 방지
        PlanCategory category = getOwnedCategoryForUpdate(userId, request);

        // PlaceCandidate 저장 -> 동시성 중복은 DB 유니크로 차단
        PlaceCandidate saved;
        try {
            saved = placeCandidateRepository.saveAndFlush(
                    PlaceCandidate.create(category, place.id(), toSnapshot(place)));
        } catch (DataIntegrityViolationException e) {
            throw ApplicationException.from(PlaceCandidateErrorCase.DUPLICATE_PLACE_CANDIDATE);
        }

        // 대표 장소 로직 - 카테고리에서 후보가 있다면 대표 장소 보장
        CategoryState state = categoryStateRepository
                .findByCategory_IdForUpdate(category.getId())
                .orElse(null);

        boolean changed = false;

        if (state == null) {
            categoryStateRepository.save(CategoryState.create(category, saved.getId()));
            changed = true;
        } else if (state.getCurrentCandidateId() == null) {
            state.changeRepresentative(saved.getId());
            changed = true;
        }

        if (changed) {
            categorySelectionLogRepository.save(
                    CategorySelectionLog.of(category.getId(), saved.getId(), LocalDateTime.now(clock)));
        }

        return PlaceCandidateCreateResponse.builder()
                .candidateId(saved.getId())
                .place(PlaceSummaryResponse.from(place))
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private PlanCategory getOwnedCategory(Long userId, PlaceCandidateCreateRequest request) {
        PlanCategory category = planCategoryRepository
                .findByIdAndPlan_IdAndDeletedAtIsNull(request.categoryId(), request.planId())
                .orElseThrow(() -> ApplicationException.from(PlaceCandidateErrorCase.INVALID_PLAN_OR_CATEGORY));
        category.getPlan().validateOwner(userId);
        return category;
    }

    private PlanCategory getOwnedCategoryForUpdate(Long userId, PlaceCandidateCreateRequest request) {
        PlanCategory category = planCategoryRepository
                .findAliveByIdAndPlanIdForUpdate(request.categoryId(), request.planId())
                .orElseThrow(() -> ApplicationException.from(PlaceCandidateErrorCase.INVALID_PLAN_OR_CATEGORY));
        category.getPlan().validateOwner(userId);
        return category;
    }

    private PlaceSnapshot toSnapshot(PlaceInfo place) {
        return new PlaceSnapshot(
                place.externalId(),
                place.name(),
                place.address(),
                place.latitude(),
                place.longitude(),
                place.isIndoor());
    }
}
