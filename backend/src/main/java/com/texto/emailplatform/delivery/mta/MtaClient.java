package com.texto.emailplatform.delivery.mta;

/**
 * SMTP transport. Implementations submit an already-composed MIME message.
 *
 * <p>Must not know about tenants, quotas, suppression, queues, or DKIM key storage.
 * Must not modify the RFC 822 payload (including {@code DKIM-Signature}).
 */
public interface MtaClient {

    String implementation();

    MtaResult submit(MtaSubmitRequest request);
}
