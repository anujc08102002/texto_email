package com.texto.emailplatform.campaign.api;
import jakarta.validation.constraints.*; import java.util.UUID;
public record SaveCampaignDraftRequest(@NotBlank @Size(max=160) String name,
    @Email @Size(max=320) @Pattern(regexp="[^\\r\\n]*") String sender,
    @Email @Size(max=320) @Pattern(regexp="[^\\r\\n]*") String replyTo,
    @Size(max=998) @Pattern(regexp="[^\\r\\n]*") String subject,
    @Size(max=500) String preheader, @Size(max=1_000_000) String htmlBody,
    @Size(max=1_000_000) String textBody, UUID templateId, @Size(max=5) java.util.List<UUID> attachmentIds) {
    public SaveCampaignDraftRequest(String name,String sender,String replyTo,String subject,String preheader,String htmlBody,String textBody,UUID templateId) {
        this(name,sender,replyTo,subject,preheader,htmlBody,textBody,templateId,java.util.List.of());
    }
}
