package com.texto.emailplatform.delivery.mta;

/**
 * Submits an already-composed RFC 822 message to an SMTP MTA.
 * Implementations must not compose MIME, sign DKIM, or alter headers/body.
 */
public interface MtaClient {

    MtaResult submit(MtaSubmitRequest request);
}
