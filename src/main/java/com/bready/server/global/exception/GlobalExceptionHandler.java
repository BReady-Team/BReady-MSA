package com.bready.server.global.exception;

import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.bready.server.global.response.CommonResponse;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final List<MissingPartErrorCaseProvider> missingPartErrorCaseProviders;

    public GlobalExceptionHandler(@Nullable List<MissingPartErrorCaseProvider> missingPartErrorCaseProviders) {
        this.missingPartErrorCaseProviders =
                missingPartErrorCaseProviders == null ? List.of() : List.copyOf(missingPartErrorCaseProviders);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse<?>> handleApplicationException(ApplicationException e) {
        log.warn("[ApplicationException] {}", e.getMessage());
        return status(e.getErrorCase().getHttpStatusCode()).body(CommonResponse.error(e.getErrorCase()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(ObjectError::getDefaultMessage)
                .orElse("요청 값이 유효하지 않습니다.");

        log.warn("[ValidationException] {}", message);
        return status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(4001, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();

        if (cause instanceof InvalidFormatException ife) {
            String fieldName = ife.getPath().isEmpty()
                    ? null
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();

            log.warn("[HttpMessageNotReadable] {}", e.getMessage());

            if ("planDate".equals(fieldName)) {
                return status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(4006, "planDate 형식이 올바르지 않습니다."));
            }

            return status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(4001, "요청 값이 유효하지 않습니다."));
        }

        log.warn("[HttpMessageNotReadable] {}", e.getMessage());
        return status(HttpStatus.BAD_REQUEST).body(CommonResponse.error(4001, "요청 값이 유효하지 않습니다."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CommonResponse<?>> handleAuthenticationException(AuthenticationException e) {
        log.warn("[AuthenticationException] {}", e.getMessage());
        return status(HttpStatus.UNAUTHORIZED).body(CommonResponse.error(401, "로그인이 필요합니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[MethodNotAllowed] {}", e.getMessage());
        return status(HttpStatus.METHOD_NOT_ALLOWED).body(CommonResponse.error(405, "허용되지 않은 HTTP 메소드입니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<?>> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[NoResourceFound] uri={}, message={}", request.getRequestURI(), e.getMessage());

        return status(HttpStatus.NOT_FOUND).body(CommonResponse.error(404, "요청하신 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<?>> handleUnexpectedException(Exception e, HttpServletRequest request)
            throws Exception {

        if (request.getRequestURI().startsWith("/actuator")) {
            throw e;
        }

        log.error("[UnexpectedException]", e);
        return status(HttpStatus.INTERNAL_SERVER_ERROR).body(CommonResponse.error(500, "서버 내부 오류가 발생했습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        throw e;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponse<?>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.warn("[MissingServletRequestParameterException] parameter={}", e.getParameterName());

        return ResponseEntity.badRequest().body(CommonResponse.error(400, "필수 요청 파라미터가 누락되었습니다."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponse<?>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("[ConstraintViolationException] {}", e.getMessage());
        return ResponseEntity.badRequest().body(CommonResponse.error(400, "요청 값이 유효하지 않습니다."));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CommonResponse<?>> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("[MissingServletRequestPartException] partName={}", e.getRequestPartName());

        // 그 파트를 책임지는 모듈이 있으면 그 모듈의 에러로 답한다
        Optional<ErrorCase> moduleErrorCase = missingPartErrorCaseProviders.stream()
                .map(provider -> provider.findErrorCase(e.getRequestPartName()))
                .flatMap(Optional::stream)
                .findFirst();

        if (moduleErrorCase.isPresent()) {
            ErrorCase errorCase = moduleErrorCase.get();
            return status(errorCase.getHttpStatusCode()).body(CommonResponse.error(errorCase));
        }

        // 그 외 일반적인 multipart 누락
        return status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error(400, "필수 요청 파트가 누락되었습니다: " + e.getRequestPartName()));
    }
}
