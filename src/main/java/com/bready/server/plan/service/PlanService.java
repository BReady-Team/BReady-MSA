package com.bready.server.plan.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.plan.domain.CategoryState;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.dto.*;
import com.bready.server.plan.exception.PlanErrorCase;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlanCategoryRepository;
import com.bready.server.plan.repository.PlanRepository;
import com.bready.server.user.api.UserApi;
import com.bready.server.user.api.UserSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanService {

    private static final String DEFAULT_OWNER_NICKNAME = "사용자";

    private final PlanRepository planRepository;
    private final PlanCategoryRepository planCategoryRepository;
    private final CategoryStateRepository categoryStateRepository;
    private final UserApi userApi;
    private final PlaceCatalogApi placeCatalogApi;

    @Transactional
    public PlanCreateResponse createPlan(Long userId, PlanCreateRequest request) {

        Plan plan = Plan.create(userId, request.getTitle(), request.getPlanDate(), request.getRegion());

        Plan saved = planRepository.save(plan);

        return PlanCreateResponse.builder()
                .planId(saved.getId())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public PlanUpdateResponse updatePlan(Long userId, Long planId, PlanUpdateRequest request) {
        Plan plan = planRepository
                .findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ApplicationException(PlanErrorCase.PLAN_NOT_FOUND));

        plan.validateOwner(userId);

        plan.update(request.getTitle(), request.getPlanDate(), request.getRegion());

        return PlanUpdateResponse.builder()
                .planId(plan.getId())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PlanDetailResponse getPlanDetail(Long userId, Long planId) {

        Plan plan = planRepository
                .findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ApplicationException(PlanErrorCase.PLAN_NOT_FOUND));

        // TODO : 플랜 공유 기능 도입 시 소유자 외에도 조회 권한 허용
        plan.validateOwner(userId);

        return buildPlanDetailResponse(plan);
    }

    @Transactional(readOnly = true)
    public PlanDetailResponse getSharedPlanDetail(String shareToken) {
        Plan plan = planRepository
                .findByShareTokenAndDeletedAtIsNull(shareToken)
                .orElseThrow(() -> new ApplicationException(PlanErrorCase.PLAN_NOT_FOUND));

        return buildPlanDetailResponse(plan);
    }

    private PlanDetailResponse buildPlanDetailResponse(Plan plan) {
        PlanDto planDto = buildPlanDto(plan);

        List<PlanCategory> categories = planCategoryRepository.findAllDetailByPlanId(plan.getId());

        if (categories.isEmpty()) {
            return PlanDetailResponse.builder()
                    .plan(planDto)
                    .categories(List.of())
                    .build();
        }

        List<Long> categoryIds = categories.stream().map(PlanCategory::getId).toList();

        Map<Long, Long> representativeMap = categoryStateRepository.findAllByCategory_IdIn(categoryIds).stream()
                .filter(cs -> cs.getCurrentCandidateId() != null)
                .collect(Collectors.toMap(cs -> cs.getCategory().getId(), CategoryState::getCurrentCandidateId));

        Map<Long, PlaceInfo> placesById = findPlacesOf(categories);

        List<PlanDetailCategoryDto> categoryDtos = categories.stream()
                .map(category -> buildCategoryDto(category, representativeMap.get(category.getId()), placesById))
                .toList();

        return PlanDetailResponse.builder()
                .plan(planDto)
                .categories(categoryDtos)
                .build();
    }

    private Map<Long, PlaceInfo> findPlacesOf(List<PlanCategory> categories) {
        List<Long> placeIds = categories.stream()
                .flatMap(category -> category.getCandidates().stream())
                .map(PlaceCandidate::getPlaceId)
                .distinct()
                .toList();

        return placeCatalogApi.getPlaces(placeIds).stream()
                .collect(Collectors.toMap(PlaceInfo::id, Function.identity()));
    }

    private PlanDto buildPlanDto(Plan plan) {
        UserSummary owner = userApi.findUserSummary(plan.getOwnerId())
                .orElseThrow(() -> ApplicationException.from(PlanErrorCase.PLAN_NOT_FOUND));

        String ownerNickname = owner.nickname() != null ? owner.nickname() : DEFAULT_OWNER_NICKNAME;
        String ownerProfileImageUrl = owner.profileImageUrl();

        return PlanDto.builder()
                .planId(plan.getId())
                .title(plan.getTitle())
                .planDate(plan.getPlanDate())
                .region(plan.getRegion())
                .status(plan.getStatus())
                .ownerNickname(ownerNickname)
                .ownerProfileImageUrl(ownerProfileImageUrl)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private PlanDetailCategoryDto buildCategoryDto(
            PlanCategory category, Long representativeId, Map<Long, PlaceInfo> placesById) {
        List<PlanDetailCandidateDto> candidateDtos = category.getCandidates().stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(candidate -> buildCandidateDto(candidate, representativeId, placesById))
                .toList();

        return PlanDetailCategoryDto.builder()
                .planCategoryId(category.getId())
                .categoryType(category.getCategoryType())
                .sequence(category.getSequence())
                .representativeCandidateId(representativeId)
                .candidates(candidateDtos)
                .build();
    }

    private PlanDetailCandidateDto buildCandidateDto(
            PlaceCandidate candidate, Long representativeId, Map<Long, PlaceInfo> placesById) {
        PlaceInfo place = placesById.get(candidate.getPlaceId());
        if (place == null) {
            // TODO: Catalog가 서비스로 나가면 "응답 없음"도 여기로 온다. 그때 장소 없이 보여 줄지 정한다
            throw new IllegalStateException("후보의 장소를 찾을 수 없습니다. candidateId=" + candidate.getId());
        }

        boolean isRepresentative = representativeId != null && representativeId.equals(candidate.getId());

        return PlanDetailCandidateDto.builder()
                .candidateId(candidate.getId())
                .isRepresentative(isRepresentative)
                .place(PlanDetailPlaceDto.builder()
                        .id(place.id())
                        .externalId(place.externalId())
                        .name(place.name())
                        .address(place.address())
                        .latitude(place.latitude())
                        .longitude(place.longitude())
                        .isIndoor(place.isIndoor())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public PlanListResponse getMyPlans(Long userId, int page, int size, SortDirection order) {
        Sort.Direction direction = (order == SortDirection.ASC) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(direction, "planDate").and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<Plan> result = planRepository.findAllByOwnerIdAndDeletedAtIsNull(userId, pageable);

        List<PlanListItemDto> items = result.getContent().stream()
                .map(plan -> PlanListItemDto.builder()
                        .planId(plan.getId())
                        .title(plan.getTitle())
                        .planDate(plan.getPlanDate())
                        .region(plan.getRegion())
                        .status(plan.getStatus())
                        .createdAt(plan.getCreatedAt())
                        .updatedAt(plan.getUpdatedAt())
                        .build())
                .toList();

        PageInfo pageInfo = PageInfo.builder()
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();

        return PlanListResponse.builder().items(items).pageInfo(pageInfo).build();
    }

    @Transactional
    public PlanDeleteResponse deletePlan(Long userId, Long planId) {

        Plan plan = planRepository
                .findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ApplicationException(PlanErrorCase.PLAN_NOT_FOUND));

        plan.validateOwner(userId);

        plan.softDelete();

        return PlanDeleteResponse.builder()
                .planId(plan.getId())
                .deletedAt(plan.getDeletedAt())
                .build();
    }

    @Transactional
    public String getOrCreateShareToken(Long userId, Long planId) {
        Plan plan = planRepository
                .findByIdAndDeletedAtIsNullForUpdate(planId)
                .orElseThrow(() -> new ApplicationException(PlanErrorCase.PLAN_NOT_FOUND));

        plan.validateOwner(userId);

        if (plan.getShareToken() != null) {
            return plan.getShareToken();
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        plan.setShareToken(token);

        return token;
    }
}
