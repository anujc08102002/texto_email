package com.texto.emailplatform.delivery;

public record OutboundMail(String from, String to, String subject, String body) {
}
