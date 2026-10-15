package com.bready.catalog.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.bready.catalog.global.response.CommonResponse;

import lombok.extern.slf4j.Slf4j;

// 오류 코드 숫자와 문구는 모놀리식 GlobalExceptionHandler와 같도록
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_VALIDATION_MESSAGE = "요청 값이 유효하지 않습니다.";

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse<Void>> handleApplicationException(ApplicationException e) {
        log.warn("[ApplicationException] {}", e.getMessage());
        ErrorCase errorCase = e.getErrorCase();
        return ResponseEntity.status(errorCase.getHttpStatusCode()).body(CommonResponse.error(errorCase));
    }

    // 쿼리 파라미터를 record로 받아서 누락,형식,범위 오류가 모두 BindException으로
    @ExceptionHandler(BindException.class)
    public ResponseEntity<CommonResponse<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(this::toClientMessage)
                .orElse(DEFAULT_VALIDATION_MESSAGE);
        log.warn("[BindException] {}", message);
        return ResponseEntity.badRequest().body(CommonResponse.error(4001, message));
    }

    // 타입 변환 실패는 스프링 내부 메시지라 응답에 내보내지 않음
    private String toClientMessage(ObjectError error) {
        if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
            return DEFAULT_VALIDATION_MESSAGE;
        }
        return error.getDefaultMessage();
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("[NoResourceFound] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.error(404, "요청하신 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("[UnexpectedException]", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponse.error(500, "서버 내부 오류가 발생했습니다."));
    }
}
