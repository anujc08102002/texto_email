package com.texto.emailplatform.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PersistentObjectStorageConfigurationTest {
    @TempDir Path directory;

    @Test
    void productionProfileWiresPersistentStorage() {
        new ApplicationContextRunner().withUserConfiguration(PersistentObjectStorageConfiguration.class)
                .withPropertyValues("spring.profiles.active=prod",
                        "email-platform.attachments.filesystem-enabled=true",
                        "email-platform.attachments.filesystem-root=" + directory)
                .run(context -> assertThat(context).hasSingleBean(ObjectStorageService.class));
    }

    @Test
    void relativeOrBlankStorageRootsAreRejected() {
        var config = new PersistentObjectStorageConfiguration();
        assertThatThrownBy(() -> config.persistentAttachmentStorage("target/uploads"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.persistentAttachmentStorage(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void objectsSurviveServiceRecreationAndRejectTraversal() throws Exception {
        var config = new PersistentObjectStorageConfiguration();
        String key = UUID.randomUUID() + "/" + UUID.randomUUID();
        byte[] content = "Persistent attachment".getBytes(StandardCharsets.UTF_8);
        config.persistentAttachmentStorage(directory.toString()).upload(
                key, "text/plain", content.length, new ByteArrayInputStream(content));
        var restarted = config.persistentAttachmentStorage(directory.toString());
        try (var input = restarted.download(key)) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
        assertThatThrownBy(() -> restarted.download("../outside")).isInstanceOf(IllegalArgumentException.class);
        restarted.delete(key);
        assertThat(restarted.metadata(key)).isEmpty();
    }
}
