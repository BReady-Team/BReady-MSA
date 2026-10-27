package com.bready.server.place.external;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchApi;
import com.bready.server.place.api.PlaceSearchResult;

import lombok.RequiredArgsConstructor;

// PlaceSearchApi의 구현을 메서드 호출에서 HTTP 호출로 변경
@Component
@RequiredArgsConstructor
public class PlaceSearchHttpAdapter implements PlaceSearchApi {

    private static final String OPERATION = "장소 검색";

    private final PlaceCatalogHttpClient placeCatalogHttpClient;

    @Override
    public List<PlaceSearchResult> searchPlaces(
            PlaceCategoryType category, String keyword, Double latitude, Double longitude, Integer radius) {
        try {
            List<PlaceSearchResult> places = placeCatalogHttpClient
                    .searchPlaces(category, keyword, latitude, longitude, radius)
                    .data();
            return places == null ? List.of() : places;
        } catch (RestClientResponseException e) {
            throw CatalogErrorTranslator.fromErrorResponse(OPERATION, e);
        } catch (ResourceAccessException e) {
            throw CatalogErrorTranslator.fromConnectionFailure(OPERATION, e);
        }
    }
}
