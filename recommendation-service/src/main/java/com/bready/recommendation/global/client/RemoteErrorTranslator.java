package com.bready.recommendation.global.client;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bready.recommendation.global.exception.ApplicationException;
import com.bready.recommendation.global.exception.ErrorCase;
import com.bready.recommendation.global.exception.RelayedErrorCase;

import lombok.extern.slf4j.Slf4j;

// 다른 서비스 호출 실패를 이 서비스의 예외로 변경
@Slf4j
public final class RemoteErrorTranslator {

    private RemoteErrorTranslator() {}

    // 상대가 CommonResponse 모양으로 답했으면 그 오류를 그대로, 아니면 연결 불가로 판단
    public static ApplicationException fromErrorResponse(
            String target, RestClientResponseException e, ErrorCase unavailable) {
        RemoteResponse<?> body = readErrorBody(e);
        if (body == null || body.errorCode() == null) {
            log.warn("{} 오류 응답을 읽지 못함 status={}", target, e.getStatusCode().value());
            return ApplicationException.from(unavailable, e);
        }
        ErrorCase relayed = new RelayedErrorCase(e.getStatusCode().value(), body.errorCode(), body.message());
        return ApplicationException.from(relayed, e);
    }

    public static ApplicationException fromConnectionFailure(
            String target, ResourceAccessException e, ErrorCase unavailable) {
        log.warn("{} 연결 실패 message={}", target, e.getMessage());
        return ApplicationException.from(unavailable, e);
    }

    private static RemoteResponse<?> readErrorBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(RemoteResponse.class);
        } catch (RestClientException conversionFailure) {
            return null;
        }
    }
}
