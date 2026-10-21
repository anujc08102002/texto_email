package com.texto.emailplatform.attachment;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Private host storage for a single-server deployment; the operator backs up this directory. */
@Configuration
@Profile("prod")
@ConditionalOnProperty(prefix = "email-platform.attachments", name = "filesystem-enabled", havingValue = "true")
public class PersistentObjectStorageConfiguration {
    @Bean
    ObjectStorageService persistentAttachmentStorage(
            @Value("${email-platform.attachments.filesystem-root}") String root) {
        if (root.isBlank() || !Path.of(root).isAbsolute()) {
            throw new IllegalStateException("EMAIL_ATTACHMENT_ROOT must be an absolute persistent directory");
        }
        return new LocalObjectStorageService(root);
    }
}
