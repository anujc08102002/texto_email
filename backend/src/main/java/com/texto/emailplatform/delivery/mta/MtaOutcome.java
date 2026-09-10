package com.texto.emailplatform.delivery.mta;

public enum MtaOutcome {
    SUCCESS,
    TEMPORARY_FAILURE,
    PERMANENT_FAILURE,
    CONNECTION_FAILURE,
    TIMEOUT,
    TLS_FAILURE
}
