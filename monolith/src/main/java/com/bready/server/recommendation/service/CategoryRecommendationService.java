package com.bready.server.recommendation.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bready.server.recommendation.cache.CategoryRecommendationCacheService;
import com.bready.server.recommendation.dto.CategoryRecommendationRequest;
import com.bready.server.recommendation.dto.CategoryRecommendationResponse;
import com.bready.server.recommendation.port.CategoryRecommendationPort;
import com.bready.server.trigger.api.TriggerContext;
import com.bready.server.trigger.api.TriggerContextApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRecommendationService {

    private final CategoryRecommendationPort categoryRecommendationPort;
    private final TriggerContextApi triggerContextApi;
    private final CategoryRecommendationCacheService categoryRecommendationCacheService;

    // 외부 응답을 기다리는 동안 DB 커넥션을 쥐지 않도록 트랜잭션 제거
    public CategoryRecommendationResponse recommendCategories(Long userId, CategoryRecommendationRequest request) {

        long start = System.currentTimeMillis();

        TriggerContext context = triggerContextApi.getTriggerContext(userId, request.triggerId());
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
