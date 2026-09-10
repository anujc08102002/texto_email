package com.texto.emailplatform.auth.api;

public record AuthResponse(String token, String tokenType, AuthUserResponse user) {

    public static AuthResponse bearer(String token, AuthUserResponse user) {
        return new AuthResponse(token, "Bearer", user);
    }
}
