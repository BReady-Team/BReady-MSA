package com.bready.recommendation.catalog.external;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.catalog.api.PlaceSearchResult;
import com.bready.recommendation.global.client.RemoteResponse;

// place-catalog-service의 내부 API 중 추천이 쓰는 것(검색)
@HttpExchange("/internal/v1/places")
public interface PlaceCatalogHttpClient {

    @GetExchange("/search")
    RemoteResponse<List<PlaceSearchResult>> searchPlaces(
            @RequestParam PlaceCategoryType category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius);
}
