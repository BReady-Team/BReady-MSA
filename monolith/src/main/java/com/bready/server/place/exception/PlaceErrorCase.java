package com.bready.server.place.exception;

import org.springframework.http.HttpStatus;

import com.bready.server.global.exception.ErrorCase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlaceErrorCase implements ErrorCase {
    LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, 4101, "위도와 경도는 함께 전달되어야 합니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, 4102, "조건에 맞는 장소를 찾을 수 없습니다."),
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
