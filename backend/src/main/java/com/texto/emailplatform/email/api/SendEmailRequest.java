package com.texto.emailplatform.email.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SendEmailRequest(
        @NotBlank @Email @Size(max = 320) String from,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<@Email @Size(max = 320) String> to,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<@Email @Size(max = 320) String> cc,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<@Email @Size(max = 320) String> bcc,
        @Email @Size(max = 320) String replyTo,
        @Size(max = 998) String subject,
        @Size(max = 500_000) String html,
        @Size(max = 500_000) String text,
        UUID templateId,
        Integer templateVersion,
        Map<String, Object> variables,
        Map<String, Object> metadata
) {
}
