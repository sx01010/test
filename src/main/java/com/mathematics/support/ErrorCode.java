package com.mathematics.support;

import org.springframework.http.HttpStatus;

/**
 * 对外错误码。与 docs/openapi/openapi-v1.yaml 的 ErrorBody.code 一一对应。
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CONTENT_NOT_VISIBLE(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_EXISTS(HttpStatus.CONFLICT),
    PROBLEM_VERSION_STALE(HttpStatus.CONFLICT),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
