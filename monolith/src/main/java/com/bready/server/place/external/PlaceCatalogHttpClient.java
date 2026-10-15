package com.bready.server.place.external;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchResult;

// place-catalog-service의 내부 API
@HttpExchange("/internal/v1/places")
public interface PlaceCatalogHttpClient {

    @GetExchange("/search")
    PlaceCatalogResponse<List<PlaceSearchResult>> searchPlaces(
            @RequestParam PlaceCategoryType category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius);
}
