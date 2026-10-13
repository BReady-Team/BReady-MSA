package com.bready.server.trigger.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.plan.repository.PlanCategoryRepository;
import com.bready.server.trigger.api.CategoryInfo;
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

    private CategoryInfo toInfo(PlanCategory category) {
        return new CategoryInfo(category.getId(), category.getCategoryType(), category.getSequence());
    }
}
