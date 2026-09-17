package com.texto.emailplatform.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.bounce.BounceTokenExtractor;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComplaintCorrelatorTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;

    private ComplaintCorrelator correlator;
    private EmailMessageEntity message;

    @BeforeEach
    void setUp() {
        correlator = new ComplaintCorrelator(emailMessageRepository, new BounceTokenExtractor(new EmailPlatformProperties()));
        message = EmailMessageEntity.create(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "noreply@acme.texto.test",
                List.of("alice@example.com"),
                List.of(),
                List.of(),
                List.of(),
                null,
                "Hello",
                null,
                "hello",
                null,
                null,
                null,
                Map.of(),
                5,
                null
        );
    }

    @Test
    void recipientAloneDoesNotCorrelate() {
        ComplaintCorrelation result = correlator.correlate(new ComplaintIngestionRequest(
                "TEST", null, null, null, "alice@example.com", null, "ABUSE", null, Map.of()
        ));
        assertThat(result.matched()).isFalse();
        assertThat(result.allowsPolicy()).isFalse();
    }

    @Test
    void tokenMatchAllowsPolicy() {
        when(emailMessageRepository.findByBounceCorrelationToken(message.getBounceCorrelationToken()))
                .thenReturn(Optional.of(message));
        ComplaintCorrelation result = correlator.correlate(ComplaintIngestionRequest.ofToken(
                message.getBounceCorrelationToken(),
                "alice@example.com"
        ));
        assertThat(result.source()).isEqualTo(ComplaintCorrelation.Source.TOKEN);
        assertThat(result.allowsPolicy()).isTrue();
    }

    @Test
    void unknownTokenDoesNotFallBackToMessageId() {
        when(emailMessageRepository.findByBounceCorrelationToken("0123456789abcdef0123456789abcdef"))
                .thenReturn(Optional.empty());
        ComplaintCorrelation result = correlator.correlate(new ComplaintIngestionRequest(
                "TEST",
                null,
                "<abc@texto.local>",
                null,
                "alice@example.com",
                "0123456789abcdef0123456789abcdef",
                "ABUSE",
                null,
                Map.of()
        ));
        assertThat(result.matched()).isFalse();
    }
}
