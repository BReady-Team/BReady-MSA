package com.bready.catalog.global.exception;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.bready.catalog.global.response.CommonResponse;

import lombok.extern.slf4j.Slf4j;

// 오류 코드 숫자와 문구는 모놀리식 GlobalExceptionHandler와 같도록
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse<Void>> handleApplicationException(ApplicationException e) {
        log.warn("[ApplicationException] {}", e.getMessage());
        ErrorCase errorCase = e.getErrorCase();
        return ResponseEntity.status(errorCase.getHttpStatusCode()).body(CommonResponse.error(errorCase));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("[MissingServletRequestParameterException] parameter={}", e.getParameterName());
        return ResponseEntity.badRequest().body(CommonResponse.error(400, "필수 요청 파라미터가 누락되었습니다."));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResponse<Void>> handleInvalidParameter(Exception e) {
        log.warn("[InvalidParameter] {}", e.getMessage());
        return ResponseEntity.badRequest().body(CommonResponse.error(400, "요청 값이 유효하지 않습니다."));
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
