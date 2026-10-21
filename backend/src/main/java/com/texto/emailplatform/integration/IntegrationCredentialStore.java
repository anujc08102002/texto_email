package com.texto.emailplatform.integration;

import java.util.Optional;
import java.util.UUID;
/** Secret-store boundary: the database persists only an opaque credential reference. */
public interface IntegrationCredentialStore {
    String store(UUID tenantId, IntegrationProvider provider, byte[] secretMaterial);
    Optional<byte[]> retrieve(UUID tenantId, String credentialReference);
    void revoke(UUID tenantId, String credentialReference);
}
