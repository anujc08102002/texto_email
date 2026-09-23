package com.texto.emailplatform.billing.razorpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.common.config.EmailPlatformConfiguration;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class RazorpayClientContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("email-platform.billing.provider=razorpay")
            .withUserConfiguration(EmailPlatformConfiguration.class, RazorpayClient.class);

    @Test
    void createsClientThroughSpringConstructorInjectionWhenRazorpayIsSelected() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RazorpayClient.class);

            RazorpayClient client = context.getBean(RazorpayClient.class);
            EmailPlatformProperties platformProperties = context.getBean(EmailPlatformProperties.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            assertThat(ReflectionTestUtils.getField(client, "properties"))
                    .isSameAs(platformProperties.getBilling().getRazorpay());
            assertThat(ReflectionTestUtils.getField(client, "objectMapper"))
                    .isSameAs(objectMapper);
        });
    }
}
