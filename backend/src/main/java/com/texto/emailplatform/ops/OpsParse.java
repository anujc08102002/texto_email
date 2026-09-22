package com.texto.emailplatform.ops;

import com.texto.emailplatform.common.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class OpsParse {

    private OpsParse() {
    }

    public static UUID uuid(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", field + " must be a UUID");
        }
    }
}
