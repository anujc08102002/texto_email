package com.texto.emailplatform.common.exception;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.api.ApiResponse.ApiFieldError;
import com.texto.emailplatform.observability.HttpRequestAuditAttributes;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, exception.getCode(), null);
        var body = ApiResponse.failure(exception.getCode(), exception.getMessage());
        var response = ResponseEntity.status(exception.getStatus());
        if (exception.getRetryAfterSeconds() != null && exception.getRetryAfterSeconds() > 0) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        }
        return response.body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "VALIDATION_ERROR", "CLIENT");
        List<ApiFieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "VALIDATION_ERROR", "CLIENT");
        List<ApiFieldError> details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "MALFORMED_REQUEST", "CLIENT");
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("MALFORMED_REQUEST", "Request body could not be read"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "UNAUTHENTICATED", "AUTH");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("UNAUTHENTICATED", "Authentication is required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "FORBIDDEN", "AUTHZ");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("FORBIDDEN", "Access is denied"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "NOT_FOUND", "CLIENT");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("NOT_FOUND", "Resource was not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, jakarta.servlet.http.HttpServletRequest request) {
        HttpRequestAuditAttributes.recordError(request, "INTERNAL_ERROR", "SERVER");
        log.error("Unhandled exception for request {}", com.texto.emailplatform.common.api.RequestIdHolder.current(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ApiFieldError toFieldError(FieldError error) {
        return new ApiFieldError(error.getField(), error.getDefaultMessage());
    }
}
