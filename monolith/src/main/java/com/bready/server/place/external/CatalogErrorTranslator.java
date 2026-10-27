package com.bready.server.place.external;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.global.exception.ErrorCase;
import com.bready.server.place.exception.PlaceErrorCase;

import lombok.extern.slf4j.Slf4j;

// Catalog 호출 실패를 모놀리식 예외로 변경
@Slf4j
final class CatalogErrorTranslator {

    private CatalogErrorTranslator() {}

    // Catalog가 CommonResponse 모양으로 답했으면 그 오류를 그대로, 아니면 연결 불가로 판단
    static ApplicationException fromErrorResponse(String operation, RestClientResponseException e) {
        PlaceCatalogResponse<?> body = readErrorBody(e);
        if (body == null || body.errorCode() == null) {
            log.warn(
                    "Catalog {} 오류 응답을 읽지 못함 status={}",
                    operation,
                    e.getStatusCode().value());
            return ApplicationException.from(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE, e);
        }
        ErrorCase relayed = new RelayedErrorCase(e.getStatusCode().value(), body.errorCode(), body.message());
        return ApplicationException.from(relayed, e);
    }

    static ApplicationException fromConnectionFailure(String operation, ResourceAccessException e) {
        log.warn("Catalog {} 연결 실패 message={}", operation, e.getMessage());
        return ApplicationException.from(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE, e);
    }

    private static PlaceCatalogResponse<?> readErrorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(PlaceCatalogResponse.class);
        } catch (RestClientException conversionFailure) {
            return null;
        }
    }
}
