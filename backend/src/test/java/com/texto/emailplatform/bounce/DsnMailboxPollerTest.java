package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DsnMailboxPollerTest {

    @Test
    void prefersOriginalRecipientHeader() {
        byte[] raw = """
                X-Original-To: bounce+0123456789abcdef0123456789abcdef@bounce.texto.test
                Delivered-To: bounce@bounce.texto.test
                To: bounce@bounce.texto.test

                body
                """.replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII);
        assertThat(DsnMailboxPoller.extractEnvelopeRecipient(raw))
                .isEqualTo("bounce+0123456789abcdef0123456789abcdef@bounce.texto.test");
    }
}
