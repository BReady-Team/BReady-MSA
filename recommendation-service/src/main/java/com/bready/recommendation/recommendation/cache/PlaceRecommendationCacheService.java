package com.bready.recommendation.recommendation.cache;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.bready.recommendation.recommendation.dto.PlaceRecommendationResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${recommendation.ai.cache.place-ttl-seconds:180}")
    private long placeCacheTtlSeconds;

    // 캐시 조회
    public List<PlaceRecommendationResponse.RecommendationItem> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        return objectMapper.convertValue(
                value, new TypeReference<List<PlaceRecommendationResponse.RecommendationItem>>() {});
    }

    // 캐시 저장
    public void put(String key, List<PlaceRecommendationResponse.RecommendationItem> items) {
        redisTemplate.opsForValue().set(key, items, Duration.ofSeconds(placeCacheTtlSeconds));
    }
}
