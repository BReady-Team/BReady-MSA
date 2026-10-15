package com.bready.catalog.global.exception;

public interface ErrorCase {

    Integer getHttpStatusCode();

    Integer getErrorCode();

    String getMessage();
}
