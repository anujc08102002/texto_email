package com.texto.emailplatform.delivery;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampaignMimeTest {
    @Test void unsubscribeHeadersAreCoveredByVerifiableDkimSignature() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var signing = mock(DkimSigningService.class);
        when(signing.sign(any(), any(), any(), any())).thenAnswer(call -> DkimSigner.sign("example.com", "texto", keys.getPrivate(), call.getArgument(2), call.getArgument(3)));
        var composer = new MimeMessageComposer(signing, "bounce.example.com");
        var request = new DeliveryEngine.DeliveryRequest(UUID.randomUUID(), "sender@example.com", List.of("a@example.com"), List.of(), List.of(), null,
                "Hello", "Hello", "<p>Hello</p>", null, "https://mail.example.com/api/v1/public/unsubscribe/" + "a".repeat(64));
        String raw = new String(composer.compose(request).request().rfc822(), StandardCharsets.UTF_8);
        assertThat(raw).contains("List-Unsubscribe: <https://mail.example.com/", "List-Unsubscribe-Post: List-Unsubscribe=One-Click");
        assertThat(DkimSigner.verify(keys.getPublic(), raw)).isTrue();
        assertThat(DkimSigner.verify(keys.getPublic(), raw.replace("One-Click", "Tampered"))).isFalse();
    }
    @Test void attachmentsRoundTripThroughNestedMimeAndRemainDkimSigned() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
        var signing=mock(DkimSigningService.class);
        when(signing.sign(any(),any(),any(),any())).thenAnswer(call->DkimSigner.sign("example.com","texto",keys.getPrivate(),call.getArgument(2),call.getArgument(3)));
        byte[] bytes=new byte[]{0,1,2,13,10,127,(byte)255};
        var composer=new MimeMessageComposer(signing,"bounce.example.com");
        var request=new DeliveryEngine.DeliveryRequest(UUID.randomUUID(),"sender@example.com",List.of("a@example.test"),List.of(),List.of(),null,"Hello","Text","<p>HTML</p>",null,null,
                List.of(new DeliveryEngine.DeliveryAttachment("customer report.txt","text/plain",bytes)));
        byte[] raw=composer.compose(request).request().rfc822();
        assertThat(DkimSigner.verify(keys.getPublic(),new String(raw,StandardCharsets.UTF_8))).isTrue();
        var parsed=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(raw));
        var mixed=(jakarta.mail.Multipart)parsed.getContent();
        assertThat(mixed.getCount()).isEqualTo(2);
        var alternatives=(jakarta.mail.Multipart)mixed.getBodyPart(0).getContent();
        assertThat(alternatives.getCount()).isEqualTo(2);
        assertThat(mixed.getBodyPart(1).getFileName()).isEqualTo("customer report.txt");
        assertThat(mixed.getBodyPart(1).getInputStream().readAllBytes()).isEqualTo(bytes);
    }
}
