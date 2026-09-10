package com.texto.emailplatform.common.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }
}
