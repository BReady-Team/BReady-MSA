package com.bready.recommendation.global.response;

import com.bready.recommendation.global.exception.ErrorCase;

import com.fasterxml.jackson.annotation.JsonInclude;

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
