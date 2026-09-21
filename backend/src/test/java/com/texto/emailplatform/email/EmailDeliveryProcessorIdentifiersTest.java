package com.texto.emailplatform.email;

import com.texto.emailplatform.delivery.DeliveryEngine;
import com.texto.emailplatform.email.domain.DeliveryAttemptRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.queue.EmailQueuePublisher;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryProcessorIdentifiersTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private DeliveryEngine deliveryEngine;
    @Mock
    private EmailQueuePublisher emailQueuePublisher;
    @Mock
    private SuppressionService suppressionService;
    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    @Test
    void successfulDeliveryStoresProviderIdRfc822IdAndKeepsBounceToken() {
        UUID tenantId = UUID.randomUUID();
        EmailMessageEntity message = EmailMessageEntity.create(
                tenantId,
                "noreply@acme.texto.test",
                List.of("a@example.com"),
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
        String token = message.getBounceCorrelationToken();
        assertThat(token).isNotBlank();

        when(emailMessageRepository.findByIdAndTenantId(message.getId(), tenantId)).thenReturn(Optional.of(message));
        when(emailMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryAttemptRepository.findByMessageIdAndTenantIdOrderByAttemptNumberAsc(message.getId(), tenantId))
                .thenReturn(List.of());
        when(deliveryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryEngine.deliver(any())).thenReturn(
                DeliveryEngine.DeliveryResult.success("ses-provider-id", "250 SES accepted")
                        .withRfc822MessageId("<rfc822@texto.local>")
        );

        EmailDeliveryProcessor processor = new EmailDeliveryProcessor(
                emailMessageRepository,
                deliveryAttemptRepository,
                deliveryEngine,
                emailQueuePublisher,
                suppressionService,
                webhookEventPublisher
        );
        processor.process(message.getId(), tenantId, 1);

        assertThat(message.getProviderMessageId()).isEqualTo("ses-provider-id");
        assertThat(message.getRfc822MessageId()).isEqualTo("<rfc822@texto.local>");
        assertThat(message.getBounceCorrelationToken()).isEqualTo(token);
    }
}
