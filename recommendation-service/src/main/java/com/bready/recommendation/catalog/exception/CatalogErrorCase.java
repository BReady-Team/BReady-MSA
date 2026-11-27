package com.bready.recommendation.catalog.exception;

import org.springframework.http.HttpStatus;

import com.bready.recommendation.global.exception.ErrorCase;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CatalogErrorCase implements ErrorCase {
    PLACE_CATALOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, 5031, "장소 서비스에 연결할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;

    @Override
    public Integer getHttpStatusCode() {
        return httpStatus.value();
    }

    @Override
    public Integer getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
