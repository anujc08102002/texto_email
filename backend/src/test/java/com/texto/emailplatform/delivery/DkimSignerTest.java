package com.texto.emailplatform.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DkimSignerTest {

    @Test
    void signsAndVerifiesRfc822() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        byte[] rfc822 = """
                From: noreply@acme.texto.test
                To: person@example.com
                Subject: Hello
                Date: Thu, 10 Sep 2026 12:00:00 +0000
                Message-ID: <msg@acme.texto.test>
                MIME-Version: 1.0
                Content-Type: text/plain; charset=UTF-8

                hello body
                """.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] signed = DkimSigner.sign(rfc822, "acme.texto.test", "texto", keys.getPrivate(), Instant.parse("2026-09-10T12:00:00Z"));
        assertThat(DkimSigner.hasSignature(signed)).isTrue();
        assertThat(new String(signed, StandardCharsets.UTF_8)).startsWith("DKIM-Signature:");
        assertThat(DkimSigner.verify(signed, keys.getPublic())).isTrue();
    }
}
