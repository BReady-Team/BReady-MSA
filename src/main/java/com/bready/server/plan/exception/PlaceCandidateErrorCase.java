package com.bready.server.plan.exception;

import org.springframework.http.HttpStatus;

import com.bready.server.global.exception.ErrorCase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlaceCandidateErrorCase implements ErrorCase {
    DUPLICATE_PLACE_CANDIDATE(HttpStatus.CONFLICT, 4106, "이미 해당 카테고리에 등록된 장소입니다."),
    INVALID_PLAN_OR_CATEGORY(HttpStatus.BAD_REQUEST, 4107, "유효하지 않은 플랜 또는 카테고리입니다."),
    PLACE_CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, 4110, "존재하지 않는 장소 후보 입니다."),
    ALREADY_REPRESENTATIVE_CANDIDATE(HttpStatus.CONFLICT, 4111, "이미 대표 장소 후보 입니다."),
    REPRESENTATIVE_CANDIDATE_CANNOT_DELETE(HttpStatus.CONFLICT, 4112, "대표 장소는 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final Integer errorCode;
    private final String message;

    @Override
    public Integer getHttpStatusCode() {
        return httpStatus.value();
    }
}
