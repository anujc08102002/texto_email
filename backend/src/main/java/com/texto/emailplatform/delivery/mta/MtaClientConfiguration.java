package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MtaClientConfiguration {

    @Bean
    MtaClient mtaClient(EmailPlatformProperties properties, SmtpSubmitter submitter) {
        return MtaClients.create(properties, submitter);
    }
}
