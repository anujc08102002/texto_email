package com.texto.emailplatform.common.api;

import com.texto.emailplatform.common.observability.RequestCorrelation;

public final class RequestIdHolder {

    public static final String HEADER = RequestCorrelation.REQUEST_ID_HEADER;
    public static final String MDC_KEY = RequestCorrelation.REQUEST_ID_MDC;

    private RequestIdHolder() {
    }

    public static String current() {
        return RequestCorrelation.requestIdOrUnknown();
    }
}
