# DKIM key custody

Private DKIM keys are stored in dedicated custody. This is not public Internet SMTP, production DNS automation, or KMS/HSM.

## Where keys are stored

Authoritative table: `dkim_keys`.

| Column | Role |
| --- | --- |
| `id` | Key identity |
| `domain_id` | Owning sending domain |
| `selector` | DKIM selector (`s=` / DNS owner name) |
| `algorithm` | `RSA` |
| `key_size` | 2048 |
| `public_key` | PKCS#1 RSA public key (Base64), used for DNS `p=` |
| `encrypted_private_key` | AES-256-GCM ciphertext (`dk1:…`) |
| `status` | `ACTIVE`, `RETIRED`, or `REVOKED` |

Constraints:

- unique `(domain_id, selector)`
- at most one `ACTIVE` key per domain

`domain_verification_records` still holds the customer-facing DKIM TXT (`<selector>._domainkey.<domain>` with `v=DKIM1; k=rsa; p=<public key>`). `private_key_ref` is a pointer (`dkim-key:<uuid>`), not PKCS#8. Legacy `pkcs8:` values are migrated into `dkim_keys` on first `ensureActiveKey`. The column is not dropped.

## Encryption

`DkimKeyProtector` (AES-256-GCM):

- 256-bit key
- unique 12-byte IV per encryption
- 128-bit authentication tag
- format `dk1:<base64(iv || ciphertext+tag)>`

Decrypt happens only when signing. Plaintext private keys are not persisted, not returned from REST, and not logged.

## Configuration

| Env / property | Purpose |
| --- | --- |
| `DKIM_KEY_ENCRYPTION_KEY` / `email-platform.domains.dkim-key-encryption-key` | Base64-encoded 32-byte AES key |

Production (`prod` profile) fails startup if the key is missing or not 32 bytes. Local and test may use the isolated SHA-256 fallback `texto-local-dkim-dev-key` when the env var is empty. Do not use that fallback in production.

Generate a production key: `openssl rand -base64 32`.

## Active key selection

`DkimKeyService.get` path:

1. Domain must belong to the requested tenant (`domains.id` + `tenant_id`).
2. Only `status = ACTIVE` is used for signing and DNS.
3. If none exists, generate RSA-2048, encrypt immediately, persist, and (when DNS records exist) point `private_key_ref` at `dkim-key:<id>`.

Cross-tenant domain or key access returns `DKIM_KEY_NOT_FOUND` with no key material.

## Signing path

```
Verified sender domain
  → DkimSigningService
    → DomainVerificationService.requireSigningMaterial
      → DkimKeyService (ACTIVE row → decrypt PKCS#8)
        → DkimSigner (rsa-sha256, relaxed/relaxed)
          → RFC 822 with DKIM-Signature
            → MtaClient
```

If the active key cannot be loaded or decrypted, delivery fails permanently (`dkim_signing_failed` / 550). There is no unsigned send, no in-process key generation at delivery, and no emergency replacement key.

## Tenant isolation

Keys are scoped by domain, and domains are scoped by tenant. Tenant B cannot retrieve or sign with tenant A’s domain or key. Exception messages do not include ciphertext or PKCS#8.

## Lifecycle

| Status | Meaning |
| --- | --- |
| `ACTIVE` | Used for signing and DNS `p=` |
| `RETIRED` | Kept after a future rotation; not used for new signatures |
| `REVOKED` | Must not be used |

Automated rotation is not implemented. A future rotation should generate a **new selector**, publish DNS, activate the new row, then retire the old row after propagation. Do not delete old keys immediately. Reusing a selector after retire would violate `unique(domain_id, selector)`.

## Current limitations

- No scheduled rotation API
- No cloud KMS/HSM
- No production DNS publisher
- Public Internet SMTP is not enabled
- Legacy `private_key_ref` may still contain `pkcs8:` until the domain is loaded; new writes use `dkim-key:<uuid>`

## Future KMS/HSM

Replace `DkimKeyProtector` (encrypt/decrypt) with a KMS-backed implementation. Ciphertext version prefix can advance (`dk2:`) without changing `dkim_keys` columns. Signing and DNS continue to read the same ACTIVE row.
