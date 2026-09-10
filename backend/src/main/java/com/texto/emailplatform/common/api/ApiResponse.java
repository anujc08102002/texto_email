package com.texto.emailplatform.common.api;

import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(boolean success, T data, ApiError error, ApiMeta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, ApiMeta.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return failure(code, message, List.of());
    }

    public static ApiResponse<Void> failure(String code, String message, List<ApiFieldError> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), ApiMeta.now());
    }

    public record ApiError(String code, String message, List<ApiFieldError> details) {
    }

    public record ApiFieldError(String field, String message) {
    }

    public record ApiMeta(String requestId, Instant timestamp) {
        public static ApiMeta now() {
            return new ApiMeta(RequestIdHolder.current(), Instant.now());
        }
    }
}
