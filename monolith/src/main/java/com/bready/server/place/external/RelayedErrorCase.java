package com.bready.server.place.external;

import com.bready.server.global.exception.ErrorCase;

// Catalog가 돌려준 오류(상태,코드,문구)를 그대로 클라이언트에 전달
public record RelayedErrorCase(int httpStatus, int errorCode, String message) implements ErrorCase {

    @Override
    public Integer getHttpStatusCode() {
        return httpStatus;
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
