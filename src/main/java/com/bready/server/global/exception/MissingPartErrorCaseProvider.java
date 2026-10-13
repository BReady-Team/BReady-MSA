package com.bready.server.global.exception;

import java.util.Optional;

// 빠진 multipart 파트를 자기 에러로 알리고 싶은 모듈이 구현
public interface MissingPartErrorCaseProvider {

    // 이 모듈이 책임지는 파트가 아니면 빈 Optional
    Optional<ErrorCase> findErrorCase(String partName);
}
