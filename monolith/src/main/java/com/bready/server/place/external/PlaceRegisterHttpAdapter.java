package com.bready.server.place.external;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.server.place.api.PlaceCatalogApi;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.place.api.PlaceRegisterRequest;

import lombok.RequiredArgsConstructor;

// PlaceCatalogApi의 구현을 모놀리식 안의 저장에서 place-catalog-service HTTP 호출로 변경
@Component
@RequiredArgsConstructor
public class PlaceRegisterHttpAdapter implements PlaceCatalogApi {

    private static final String OPERATION = "장소 등록";

    private final PlaceCatalogHttpClient placeCatalogHttpClient;

    @Override
    public PlaceInfo register(PlaceRegisterRequest request) {
        try {
            return placeCatalogHttpClient.registerPlace(request).data();
        } catch (RestClientResponseException e) {
            throw CatalogErrorTranslator.fromErrorResponse(OPERATION, e);
        } catch (ResourceAccessException e) {
            throw CatalogErrorTranslator.fromConnectionFailure(OPERATION, e);
        }
    }
}
