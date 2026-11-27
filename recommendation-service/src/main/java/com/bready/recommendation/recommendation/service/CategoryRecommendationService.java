package com.bready.recommendation.recommendation.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bready.recommendation.planning.api.TriggerContext;
import com.bready.recommendation.planning.api.TriggerContextApi;
import com.bready.recommendation.recommendation.cache.CategoryRecommendationCacheService;
import com.bready.recommendation.recommendation.dto.CategoryRecommendationRequest;
import com.bready.recommendation.recommendation.dto.CategoryRecommendationResponse;
import com.bready.recommendation.recommendation.port.CategoryRecommendationPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRecommendationService {

    private final CategoryRecommendationPort categoryRecommendationPort;
    private final TriggerContextApi triggerContextApi;
    private final CategoryRecommendationCacheService categoryRecommendationCacheService;

    public CategoryRecommendationResponse recommendCategories(
            String authorization, CategoryRecommendationRequest request) {

        long start = System.currentTimeMillis();

        TriggerContext context = triggerContextApi.getTriggerContext(authorization, request.triggerId());
        long t1 = System.currentTimeMillis();

        String cacheKey = categoryRecommendationCacheService.generateKey(request.triggerId());

        List<CategoryRecommendationResponse.CategoryItem> cachedItems =
                categoryRecommendationCacheService.get(cacheKey);

        if (cachedItems != null && !cachedItems.isEmpty()) {
            return new CategoryRecommendationResponse(cachedItems);
        }

        List<CategoryRecommendationResponse.CategoryItem> items = categoryRecommendationPort.recommendCategories(
                context.planCategories(), context.currentCategory(), context.triggerType());
        long t2 = System.currentTimeMillis();

        log.info("categoryReco validate={}ms, recommend={}ms, total={}ms", t1 - start, t2 - t1, t2 - start);

        categoryRecommendationCacheService.put(cacheKey, items);

        return new CategoryRecommendationResponse(items);
    }
}
