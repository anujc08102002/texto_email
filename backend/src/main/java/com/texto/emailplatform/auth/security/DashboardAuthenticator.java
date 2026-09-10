package com.texto.emailplatform.auth.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Dashboard authentication contract. Session/JWT issuance is intentionally deferred.
 */
public interface DashboardAuthenticator {

    Optional<DashboardPrincipal> authenticate(String email, String password);
}
