package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MtaClientConfiguration {

    @Bean
    MtaClient mtaClient(
            EmailPlatformProperties properties,
            SmtpSubmitter submitter,
            PublicDeliveryGuard publicDeliveryGuard,
            SesMtaClient sesMtaClient
    ) {
        return MtaClients.create(properties, submitter, publicDeliveryGuard, sesMtaClient);
    }

    @Bean
    SesMtaClient sesMtaClient(
            SesV2Client sesV2Client,
            EmailPlatformProperties properties,
            PublicDeliveryGuard publicDeliveryGuard
    ) {
        return new SesMtaClient(sesV2Client, properties, publicDeliveryGuard);
    }

    @Bean
    SesV2Client sesV2Client(EmailPlatformProperties properties) {
        return SesV2Client.builder()
                .region(Region.of(properties.getSes().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
