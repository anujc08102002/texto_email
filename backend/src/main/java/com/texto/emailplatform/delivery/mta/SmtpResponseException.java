package com.texto.emailplatform.delivery.mta;

import java.io.IOException;

public class SmtpResponseException extends IOException {

    private final int code;
    private final String response;

    public SmtpResponseException(int code, String response) {
        super(response == null ? "SMTP " + code : response);
        this.code = code;
        this.response = response == null ? "" : response;
    }

    public SmtpResponseException(String response) {
        this(parseCode(response), response);
    }

    public int code() {
        return code;
    }

    public String response() {
        return response;
    }

    static int parseCode(String response) {
        if (response == null || response.length() < 3) {
            return 0;
        }
        try {
            return Integer.parseInt(response.substring(0, 3));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
