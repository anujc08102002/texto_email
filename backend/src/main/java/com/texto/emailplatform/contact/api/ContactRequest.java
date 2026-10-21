package com.texto.emailplatform.contact.api;
import jakarta.validation.constraints.*;
public record ContactRequest(@NotBlank @Email @Size(max=320) String email,
    @Size(max=120) String firstName, @Size(max=120) String lastName,
    @Size(max=200) String company, @Size(max=64) String phone, @Size(max=64) String source,
    @Size(max=50_000) String properties,
    @Pattern(regexp="SUBSCRIBED|UNSUBSCRIBED|SUPPRESSED") String subscriptionStatus) {}
