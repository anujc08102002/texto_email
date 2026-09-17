package com.texto.emailplatform.delivery.mta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public final class PostfixTestContainer {

    private PostfixTestContainer() {
    }

    public static GenericContainer<?> create() {
        return new GenericContainer<>(image())
                .withExposedPorts(25)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("tc.postfix")))
                .waitingFor(Wait.forLogMessage(".*daemon started.*", 1).withStartupTimeout(Duration.ofMinutes(4)))
                .withStartupTimeout(Duration.ofMinutes(4));
    }

    private static ImageFromDockerfile image() {
        Path dir = locatePostfixDir();
        ImageFromDockerfile image = new ImageFromDockerfile("email-platform-postfix", false);
        for (String name : List.of(
                "Dockerfile",
                "main.cf",
                "docker-entrypoint.sh",
                "smtp-ping.sh",
                "virtual_mailbox_maps.regexp",
                "recipient_access.regexp"
        )) {
            Path file = dir.resolve(name);
            if (!Files.exists(file)) {
                throw new IllegalStateException("Missing Postfix build file: " + file);
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
                image.withFileFromString(name, content);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read " + file, exception);
            }
        }
        return image;
    }

    static Path locatePostfixDir() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = {
                cwd.resolve("infrastructure/postfix"),
                cwd.resolve("../infrastructure/postfix"),
                cwd.resolve("../../infrastructure/postfix")
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.exists(normalized.resolve("Dockerfile"))) {
                return normalized;
            }
        }
        throw new IllegalStateException("Could not locate infrastructure/postfix/Dockerfile from " + cwd);
    }
}
