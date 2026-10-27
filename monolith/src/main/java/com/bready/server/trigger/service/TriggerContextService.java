package com.bready.server.trigger.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.plan.domain.CategoryState;
import com.bready.server.plan.domain.PlaceCandidate;
import com.bready.server.plan.domain.PlaceSnapshot;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.exception.PlaceCandidateErrorCase;
import com.bready.server.plan.repository.CategoryStateRepository;
import com.bready.server.plan.repository.PlaceCandidateRepository;
import com.bready.server.plan.repository.PlanCategoryRepository;
import com.bready.server.trigger.api.CategoryInfo;
import com.bready.server.trigger.api.RepresentativePlace;
import com.bready.server.trigger.api.TriggerContext;
import com.bready.server.trigger.api.TriggerContextApi;
import com.bready.server.trigger.domain.Trigger;
import com.bready.server.trigger.exception.TriggerErrorCase;
import com.bready.server.trigger.repository.TriggerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TriggerContextService implements TriggerContextApi {

    private final TriggerRepository triggerRepository;
    private final PlanCategoryRepository planCategoryRepository;
    private final CategoryStateRepository categoryStateRepository;
    private final PlaceCandidateRepository placeCandidateRepository;

    @Override
    public TriggerContext getTriggerContext(Long userId, Long triggerId) {
        Trigger trigger = triggerRepository
                .findByIdAndDeletedAtIsNull(triggerId)
                .orElseThrow(() -> ApplicationException.from(TriggerErrorCase.TRIGGER_NOT_FOUND));

        Plan plan = trigger.getPlan();
        plan.validateOwner(userId);

        List<CategoryInfo> planCategories =
                planCategoryRepository.findAllByPlan_IdAndDeletedAtIsNullOrderBySequenceAsc(plan.getId()).stream()
                        .map(this::toInfo)
                        .toList();

        return new TriggerContext(
                trigger.getId(),
                trigger.getTriggerType(),
                plan.getRegion(),
                toInfo(trigger.getCategory()),
                planCategories);
    }

    @Override
    public RepresentativePlace getRepresentativePlace(Long categoryId) {
        Long representativeCandidateId = categoryStateRepository
                .findByCategory_Id(categoryId)
                .map(CategoryState::getCurrentCandidateId)
                .orElseThrow(() -> ApplicationException.from(TriggerErrorCase.CATEGORY_STATE_NOT_FOUND));

        return placeCandidateRepository
                .findAliveById(representativeCandidateId)
                .map(PlaceCandidate::getPlaceSnapshot)
                .map(this::toRepresentativePlace)
                .orElseThrow(() -> ApplicationException.from(PlaceCandidateErrorCase.PLACE_CANDIDATE_NOT_FOUND));
    }

    private CategoryInfo toInfo(PlanCategory category) {
        return new CategoryInfo(category.getId(), category.getCategoryType(), category.getSequence());
    }

    private RepresentativePlace toRepresentativePlace(PlaceSnapshot snapshot) {
        return new RepresentativePlace(
                snapshot.externalId(), snapshot.address(), snapshot.latitude(), snapshot.longitude());
    }
}
