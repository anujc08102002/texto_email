package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class BounceCorrelatorTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;

    private BounceCorrelator correlator;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        correlator = new BounceCorrelator(emailMessageRepository, new BounceTokenExtractor(new EmailPlatformProperties()));
        tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    }

    @Test
    void tokenLookupIsAuthoritativeForMutation() {
        EmailMessageEntity message = outbound(tenantA, "<abc@texto.local>");
        when(emailMessageRepository.findByBounceCorrelationToken(message.getBounceCorrelationToken()))
                .thenReturn(Optional.of(message));

        ParsedDsn dsn = ParsedDsn.success(null, null, null, "<other@texto.local>", null, null, List.of());
        String envelope = BounceAddress.format(message.getBounceCorrelationToken(), "bounce.texto.test");
        BounceCorrelation correlation = correlator.correlate(dsn, null, envelope);
        assertThat(correlation.matched()).isTrue();
        assertThat(correlation.allowsStateMutation()).isTrue();
        assertThat(correlation.source()).isEqualTo(BounceCorrelation.Source.TOKEN);
    }

    @Test
    void unknownTokenDoesNotFallBackToForeignMessageId() {
        EmailMessageEntity other = outbound(tenantB, "<abc@texto.local>");
        String unknown = "ffffffffffffffffffffffffffffffff";
        when(emailMessageRepository.findByBounceCorrelationToken(unknown)).thenReturn(Optional.empty());

        ParsedDsn dsn = ParsedDsn.success(null, null, null, other.getRfc822MessageId(), null, null, List.of());
        BounceCorrelation correlation = correlator.correlate(
                dsn,
                null,
                BounceAddress.format(unknown, "bounce.texto.test")
        );
        assertThat(correlation.matched()).isFalse();
        assertThat(correlation.allowsStateMutation()).isFalse();
        verify(emailMessageRepository, never()).findByRfc822MessageId(any());
    }

    @Test
    void messageIdFallbackDoesNotAuthorizeMutation() {
        EmailMessageEntity message = outbound(tenantA, "<abc@texto.local>");
        when(emailMessageRepository.findByRfc822MessageId("<abc@texto.local>")).thenReturn(List.of(message));

        ParsedDsn dsn = ParsedDsn.success(null, null, null, "<abc@texto.local>", null, null, List.of());
        BounceCorrelation correlation = correlator.correlate(dsn, null, "alice@example.com");
        assertThat(correlation.matched()).isTrue();
        assertThat(correlation.allowsStateMutation()).isFalse();
        assertThat(correlation.source()).isEqualTo(BounceCorrelation.Source.MESSAGE_ID);
    }

    private static EmailMessageEntity outbound(UUID tenantId, String rfc822MessageId) {
        EmailMessageEntity entity = EmailMessageEntity.create(
                tenantId,
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
        entity.recordRfc822MessageId(rfc822MessageId);
        return entity;
    }
}
