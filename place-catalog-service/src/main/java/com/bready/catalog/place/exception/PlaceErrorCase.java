package com.bready.catalog.place.exception;

import org.springframework.http.HttpStatus;

import com.bready.catalog.global.exception.ErrorCase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 코드 숫자는 모놀리식 PlaceErrorCase와 동일
@Getter
@RequiredArgsConstructor
public enum PlaceErrorCase implements ErrorCase {
    KAKAO_CLIENT_ERROR(HttpStatus.BAD_GATEWAY, 4103, "카카오 장소 검색 요청이 잘못되었습니다."),
    KAKAO_SERVER_ERROR(HttpStatus.BAD_GATEWAY, 4104, "카카오 장소 서비스에 장애가 발생했습니다."),
    KAKAO_RESPONSE_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 4105, "카카오 장소 응답 파싱에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;

    @Override
    public Integer getHttpStatusCode() {
        return httpStatus.value();
    }
}
