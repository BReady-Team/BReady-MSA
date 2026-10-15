package com.bready.catalog.global.response;

import com.bready.catalog.global.exception.ErrorCase;

import com.fasterxml.jackson.annotation.JsonInclude;

// 모놀리식과 같은 응답 JSON 모양을 지킨다. 공유하는 것은 클래스가 아니라 이 모양이다
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommonResponse<T>(Integer errorCode, String message, T data) {

    private static final String SUCCESS_MESSAGE = "success";

    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(null, SUCCESS_MESSAGE, data);
    }

    public static CommonResponse<Void> error(ErrorCase errorCase) {
        return new CommonResponse<>(errorCase.getErrorCode(), errorCase.getMessage(), null);
    }

    public static CommonResponse<Void> error(int errorCode, String message) {
        return new CommonResponse<>(errorCode, message, null);
    }
}
