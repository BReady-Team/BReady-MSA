package com.bready.server.place.external;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.global.exception.ErrorCase;
import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchApi;
import com.bready.server.place.api.PlaceSearchResult;
import com.bready.server.place.exception.PlaceErrorCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// PlaceSearchApi의 구현을 메서드 호출에서 HTTP 호출로 변경
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceSearchHttpAdapter implements PlaceSearchApi {

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
            throw ApplicationException.from(toErrorCase(e), e);
        } catch (ResourceAccessException e) {
            log.warn("Catalog 장소 검색 연결 실패 message={}", e.getMessage());
            throw ApplicationException.from(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE, e);
        }
    }

    // Catalog가 CommonResponse 모양으로 답했으면 그 오류를 그대로, 아니면 연결 불가로 판단
    private ErrorCase toErrorCase(RestClientResponseException e) {
        PlaceCatalogResponse<?> body = readErrorBody(e);
        if (body == null || body.errorCode() == null) {
            log.warn("Catalog 장소 검색 오류 응답을 읽지 못함 status={}", e.getStatusCode().value());
            return PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE;
        }
        return new RelayedErrorCase(e.getStatusCode().value(), body.errorCode(), body.message());
    }

    private PlaceCatalogResponse<?> readErrorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(PlaceCatalogResponse.class);
        } catch (RestClientException conversionFailure) {
            return null;
        }
    }
}
