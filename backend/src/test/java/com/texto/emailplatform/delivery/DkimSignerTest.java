package com.texto.emailplatform.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class DkimSignerTest {

    @Test
    void signsWithRsaSha256AndMatchingBodyHash() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("from", "noreply@acme.texto.test");
        headers.put("to", "person@example.com");
        headers.put("subject", "Hello");
        String body = "hello world\n";

        String header = DkimSigner.sign("acme.texto.test", "texto", keyPair.getPrivate(), headers, body);
        String unfolded = header.replaceAll("\r\n[ \t]+", " ");

        assertThat(unfolded).startsWith("DKIM-Signature:");
        assertThat(unfolded).contains("a=rsa-sha256");
        assertThat(unfolded).contains("d=acme.texto.test");
        assertThat(unfolded).contains("s=texto");
        assertThat(unfolded).contains("bh=" + DkimSigner.bodyHash(body));
        assertThat(unfolded).containsPattern("b=[A-Za-z0-9+/=]+");

        String rfc822 = header + "\r\nFrom: noreply@acme.texto.test\r\nTo: person@example.com\r\nSubject: Hello\r\n\r\n" + body.replace("\n", "\r\n");
        assertThat(DkimSigner.verify(keyPair.getPublic(), rfc822)).isTrue();
    }
}
