package com.texto.emailplatform.delivery.mta;

/**
 * Transport-level SMTP outcome. Retry/state transitions belong to the delivery state machine.
 */
public enum MtaOutcome {
    SUCCESS,
    TEMPORARY_FAILURE,
    PERMANENT_FAILURE,
    CONNECTION_FAILURE,
    TLS_FAILURE,
    TIMEOUT,
    UNKNOWN_FAILURE
}
