package com.bready.server.s3.exception;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.bready.server.global.exception.ErrorCase;
import com.bready.server.global.exception.MissingPartErrorCaseProvider;

// 파일을 받는 API(파일 업로드, 프로필 이미지)는 파트 이름으로 file을 사용
@Component
public class S3MissingPartErrorCaseProvider implements MissingPartErrorCaseProvider {

    private static final String FILE_PART_NAME = "file";

    @Override
    public Optional<ErrorCase> findErrorCase(String partName) {
        if (FILE_PART_NAME.equals(partName)) {
            return Optional.of(S3ErrorCase.FILE_REQUIRED);
        }
        return Optional.empty();
    }
}
