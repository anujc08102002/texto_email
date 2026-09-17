package com.texto.emailplatform.delivery;

/**
 * DKIM signing failed. Delivery must not continue unsigned.
 * Messages must not include private-key material.
 */
public class DkimSigningException extends IllegalStateException {

    public DkimSigningException(String message) {
        super(message);
    }

    public DkimSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
