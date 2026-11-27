package com.bready.recommendation.catalog.external;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.catalog.api.PlaceSearchApi;
import com.bready.recommendation.catalog.api.PlaceSearchResult;
import com.bready.recommendation.catalog.exception.CatalogErrorCase;
import com.bready.recommendation.global.client.RemoteErrorTranslator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlaceSearchHttpAdapter implements PlaceSearchApi {

    private static final String TARGET = "Catalog 장소 검색";

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
            throw RemoteErrorTranslator.fromErrorResponse(TARGET, e, CatalogErrorCase.PLACE_CATALOG_UNAVAILABLE);
        } catch (ResourceAccessException e) {
            throw RemoteErrorTranslator.fromConnectionFailure(TARGET, e, CatalogErrorCase.PLACE_CATALOG_UNAVAILABLE);
        }
    }
}
