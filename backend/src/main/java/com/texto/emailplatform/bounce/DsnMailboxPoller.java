package com.texto.emailplatform.bounce;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional maildir consumer for a local Postfix bounce spool. Disabled unless
 * {@code email-platform.bounce.spool-directory} is set. Not a public MX.
 */
@Component
@ConditionalOnProperty(prefix = "email-platform.bounce", name = "spool-directory")
public class DsnMailboxPoller {

    private static final Logger log = LoggerFactory.getLogger(DsnMailboxPoller.class);

    private final TrustedDsnIngressService ingressService;
    private final com.texto.emailplatform.common.config.EmailPlatformProperties properties;

    public DsnMailboxPoller(
            TrustedDsnIngressService ingressService,
            com.texto.emailplatform.common.config.EmailPlatformProperties properties
    ) {
        this.ingressService = ingressService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${email-platform.bounce.spool-poll-ms:2000}")
    public void poll() {
        String configured = properties.getBounce().getSpoolDirectory();
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path newDir = Path.of(configured).resolve("new");
        if (!Files.isDirectory(newDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(newDir)) {
            for (Path file : stream) {
                consume(file);
            }
        } catch (Exception exception) {
            log.warn("Bounce spool poll failed type={}", exception.getClass().getSimpleName());
        }
    }

    private void consume(Path file) {
        try {
            byte[] raw = Files.readAllBytes(file);
            String envelope = extractEnvelopeRecipient(raw);
            ingressService.accept(envelope, raw);
            Files.deleteIfExists(file);
        } catch (Exception exception) {
            log.warn("Bounce spool file skipped type={}", exception.getClass().getSimpleName());
        }
    }

    static String extractEnvelopeRecipient(byte[] raw) {
        String text = new String(raw, StandardCharsets.US_ASCII);
        String originalTo = header(text, "X-Original-To");
        if (originalTo != null) {
            return originalTo;
        }
        String deliveredTo = header(text, "Delivered-To");
        if (deliveredTo != null) {
            return deliveredTo;
        }
        return header(text, "To");
    }

    private static String header(String rfc822, String name) {
        String prefix = name + ":";
        for (String line : rfc822.replace("\r\n", "\n").split("\n")) {
            if (line.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
