package com.texto.emailplatform.common.exception;

public class ApiException extends RuntimeException {

    private final String code;
    private final int status;
    private final Integer retryAfterSeconds;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
