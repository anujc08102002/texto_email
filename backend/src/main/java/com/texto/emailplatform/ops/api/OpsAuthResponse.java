package com.texto.emailplatform.ops.api;

public record OpsAuthResponse(String token, String tokenType, OpsAdminResponse admin) {
}
