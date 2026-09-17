package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class MtaHealthIndicatorTest {

    @Test
    void upWhenSmtpBannerIs220() throws Exception {
        CountDownLatch served = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(() -> {
                try (Socket socket = server.accept();
                     var out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
                     var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                    out.write("220 mail.texto.test ESMTP\r\n");
                    out.flush();
                    in.readLine();
                    out.write("221 bye\r\n");
                    out.flush();
                    served.countDown();
                } catch (Exception ignored) {
                    // test socket
                }
            });
            thread.setDaemon(true);
            thread.start();

            EmailPlatformProperties properties = new EmailPlatformProperties();
            properties.getMta().setImplementation("postfix");
            properties.getMta().getSmtp().setHost("127.0.0.1");
            properties.getMta().getSmtp().setPort(server.getLocalPort());
            properties.getMta().getSmtp().setConnectionTimeoutMs(1000);

            var health = new MtaHealthIndicator(properties).health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get("reachable")).isEqualTo(true);
            assertThat(health.getDetails().get("recipientDelivery")).isEqualTo("not_checked");
            assertThat(health.getDetails().get("internetDelivery")).isEqualTo("disabled");
            assertThat(health.getDetails().get("publicDeliveryEnabled")).isEqualTo(false);
            assertThat(health.getDetails().get("ptrVerified")).isEqualTo(false);
            assertThat(health.getDetails().get("productionInternetReady")).isEqualTo(false);
            assertThat(health.getDetails().get("publicDeliverySwitch")).isEqualTo("NOT_CONFIGURED");
            assertThat(served.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void downWhenNothingListens() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().getSmtp().setHost("127.0.0.1");
        properties.getMta().getSmtp().setPort(1);
        properties.getMta().getSmtp().setConnectionTimeoutMs(250);
        var health = new MtaHealthIndicator(properties).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reachable")).isEqualTo(false);
    }

    @Test
    void sesHealthDoesNotOpenSmtpSocket() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("ses");
        properties.getSes().setEnabled(true);
        properties.getSes().setRegion("ap-south-1");
        var health = new MtaHealthIndicator(properties).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("provider")).isEqualTo("sesv2");
        assertThat(health.getDetails().get("reachable")).isEqualTo("not_checked");
        assertThat(health.getDetails().get("region")).isEqualTo("ap-south-1");
    }
}
