package com.texto.emailplatform.delivery;

public interface OutboundMailTransport {

    void send(OutboundMail mail);
}
