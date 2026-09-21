package com.texto.emailplatform.sesfeedback;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(prefix = "email-platform.ses-feedback", name = "enabled", havingValue = "true")
public class SesFeedbackAwsConfiguration {

    @Bean
    SqsClient sqsClient(EmailPlatformProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getSes().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
