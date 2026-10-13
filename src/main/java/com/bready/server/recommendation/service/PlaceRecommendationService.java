package com.bready.server.recommendation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.recommendation.cache.PlaceRecommendationCacheService;
import com.bready.server.recommendation.dto.PlaceRecommendationQuery;
import com.bready.server.recommendation.dto.PlaceRecommendationRequest;
import com.bready.server.recommendation.dto.PlaceRecommendationResponse;
import com.bready.server.recommendation.port.PlaceRecommendationPort;
import com.bready.server.trigger.api.CategoryInfo;
import com.bready.server.trigger.api.TriggerContext;
import com.bready.server.trigger.api.TriggerContextApi;
import com.bready.server.trigger.api.TriggerType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceRecommendationService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private static final int DEFAULT_RADIUS = 2000;

    private final TriggerContextApi triggerContextApi;
    private final PlaceCatalogApi placeCatalogApi;
    private final PlaceRecommendationPort placeRecommendationPort;
    private final PlaceRecommendationCacheService placeRecommendationCacheService;

    @Transactional(readOnly = true)
    public PlaceRecommendationResponse recommendPlaces(
            Long userId, PlaceRecommendationRequest request, PlaceRecommendationQuery query) {

        long start = System.currentTimeMillis();

        // 트리거 조회와 소유권 검증은 Planning으로
        TriggerContext context = triggerContextApi.getTriggerContext(userId, request.triggerId());

        long t1 = System.currentTimeMillis();

        TriggerType triggerType = context.triggerType();
        CategoryInfo category = context.currentCategory();

        if (triggerType == TriggerType.WEATHER_BAD && !category.categoryType().isIndoor()) {
            return new PlaceRecommendationResponse(List.of());
        }

        int limit = normalizeSize(query.size());
        int radius = (query.radius() != null) ? query.radius() : DEFAULT_RADIUS;

        ResolvedBase resolved = resolveBase(triggerType, category.categoryId(), query);
        long t2 = System.currentTimeMillis();

        String region = firstNonBlank(
                normalizeRegion(resolved.region()),
                normalizeRegion(query.region()),
                normalizeRegion(context.planRegion()));

        Coordinate base = resolved.coordinate();

        // 캐시 key 생성
        String cacheKey = placeRecommendationCacheService.generateKey(
                category.categoryType().name(), base.latitude, base.longitude);

        // 캐시 조회
        List<PlaceRecommendationResponse.RecommendationItem> cachedItems =
                placeRecommendationCacheService.get(cacheKey);

        // 캐시 HIT
        if (cachedItems != null && !cachedItems.isEmpty()) {
            long cachedHitTime = System.currentTimeMillis();
            log.info("placeReco cacheHit total={}ms, key={}", cachedHitTime - start, cacheKey);
            return new PlaceRecommendationResponse(cachedItems);
        }

        // 캐시 MISS
        List<PlaceRecommendationResponse.RecommendationItem> items = placeRecommendationPort.recommendPlaceCandidates(
                category.categoryType(),
                triggerType,
                region,
                base.latitude(),
                base.longitude(),
                radius,
                limit,
                resolved.excludeExternalId());

        long t3 = System.currentTimeMillis();

        if (items.isEmpty()) {
            return new PlaceRecommendationResponse(List.of());
        }

        placeRecommendationCacheService.put(cacheKey, items);

        log.info(
                "placeReco validate={}ms, resolveBase={}ms, portCall={}ms, total={}ms",
                t1 - start,
                t2 - t1,
                t3 - t2,
                t3 - start);

        return new PlaceRecommendationResponse(items);
    }

    private ResolvedBase resolveBase(TriggerType triggerType, Long categoryId, PlaceRecommendationQuery query) {

        // trigger 중 현재 위치 기반으로 탐색해야 하는 trigger
        if (triggerType == TriggerType.FATIGUE || triggerType == TriggerType.DISTANCE_TOO_FAR) {
            if (query.latitude() != null && query.longitude() != null) {
                return new ResolvedBase(new Coordinate(query.latitude(), query.longitude()), null, null);
            }
        }

        // 기존 대표장소 기반 trigger -> 대표 후보가 가리키는 장소는 Planning에, 그 장소의 좌표와 주소는 Place Catalog에
        Long placeId = triggerContextApi.getRepresentativePlaceId(categoryId);

        PlaceInfo place = placeCatalogApi.getPlaces(List.of(placeId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("대표 후보의 장소를 찾을 수 없습니다. placeId=" + placeId));

        Coordinate coordinate =
                new Coordinate(place.latitude().doubleValue(), place.longitude().doubleValue());

        return new ResolvedBase(coordinate, extractRegionFromAddress(place.address()), place.externalId());
    }

    private String extractRegionFromAddress(String address) {
        if (address == null) return null;
        String a = address.trim();
        if (a.isEmpty()) return null;

        String[] parts = a.split("\\s+");
        if (parts.length >= 2) return parts[0] + " " + parts[1];
        if (parts.length == 1) return parts[0];
        return null;
    }

    private int normalizeSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    private String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        if (c != null && !c.isBlank()) return c;
        return null;
    }

    private String normalizeRegion(String region) {
        if (region == null) return null;
        String r = region.trim();
        if (r.isEmpty()) return null;
        if ("string".equalsIgnoreCase(r)) return null;
        if ("null".equalsIgnoreCase(r)) return null;
        return r;
    }

    private record Coordinate(Double latitude, Double longitude) {}

    private record ResolvedBase(Coordinate coordinate, String region, String excludeExternalId) {}
}
