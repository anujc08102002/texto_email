package com.texto.emailplatform.apikey.api;

/**
 * Returned once at creation time: {@code secret} is the only moment the raw key is available.
 */
public record CreatedApiKeyResponse(ApiKeyResponse apiKey, String secret) {
}
