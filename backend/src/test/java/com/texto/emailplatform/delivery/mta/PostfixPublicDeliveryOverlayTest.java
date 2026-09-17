package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostfixPublicDeliveryOverlayTest {

    @Test
    void entrypointKeepsPublicDeliveryOffUnlessConfirmed() throws Exception {
        String entrypoint = Files.readString(script("docker-entrypoint.sh"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String mainCf = Files.readString(script("main.cf"), StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertThat(entrypoint).contains("POSTFIX_PUBLIC_DELIVERY_CONFIRM");
        assertThat(entrypoint).contains("ENABLE_PUBLIC_MX_DELIVERY");
        assertThat(entrypoint).contains("POSTFIX_ENABLE_PUBLIC_DELIVERY must be 'no' or 'yes'");
        assertThat(entrypoint).contains("0.0.0.0/0");
        assertThat(entrypoint).contains("default_transport = smtp");
        assertThat(entrypoint).contains("permit_mynetworks, reject_unauth_destination");
        assertThat(entrypoint).contains("smtp_tls_security_level = may");
        assertThat(entrypoint).contains("POSTFIX_SMTP_HELO_NAME must match POSTFIX_MYHOSTNAME");
        assertThat(entrypoint).doesNotContain("smtpd_relay_restrictions = permit_all");
        assertThat(entrypoint).doesNotContain("mynetworks = 0.0.0.0/0");

        assertThat(mainCf).contains("default_transport = error:public Internet delivery is disabled in this MTA");
        assertThat(mainCf).contains("smtpd_relay_restrictions = reject_unauth_destination");
        assertThat(mainCf).contains("smtpd_client_restrictions = permit_mynetworks, reject");
        assertThat(mainCf).contains("local_header_rewrite_clients =");
    }

    @Test
    void smtpSubmitterDoesNotDisableCertificateVerification() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/texto/emailplatform/delivery/mta/SmtpSubmitter.java"),
                StandardCharsets.UTF_8
        );
        assertThat(source).contains("setEndpointIdentificationAlgorithm(\"HTTPS\")");
        assertThat(source).doesNotContain("TrustAll");
        assertThat(source).doesNotContain("InsecureTrustManager");
        assertThat(source).doesNotContain("setHostnameVerifier");
    }

    private static Path script(String name) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = {
                cwd.resolve("infrastructure/postfix").resolve(name),
                cwd.resolve("../infrastructure/postfix").resolve(name)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Missing " + name + " from " + cwd);
    }
}
