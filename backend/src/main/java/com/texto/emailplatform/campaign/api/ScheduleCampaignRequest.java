package com.texto.emailplatform.campaign.api; import jakarta.validation.constraints.*; import java.time.Instant;
public record ScheduleCampaignRequest(@NotNull @Future Instant scheduledAt,@NotBlank @Size(max=64) String timezone){}
