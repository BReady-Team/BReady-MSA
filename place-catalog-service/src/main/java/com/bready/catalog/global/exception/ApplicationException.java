package com.bready.catalog.global.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {

    private final ErrorCase errorCase;

    private ApplicationException(ErrorCase errorCase, Throwable cause) {
        super(errorCase.getMessage(), cause);
        this.errorCase = errorCase;
    }

    public static ApplicationException from(ErrorCase errorCase) {
        return new ApplicationException(errorCase, null);
    }

    public static ApplicationException from(ErrorCase errorCase, Throwable cause) {
        return new ApplicationException(errorCase, cause);
    }
}
