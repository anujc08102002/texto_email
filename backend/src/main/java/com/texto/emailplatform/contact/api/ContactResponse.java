package com.texto.emailplatform.contact.api;
import java.time.Instant; import java.util.UUID;
public record ContactResponse(UUID id, String email, String firstName, String lastName, String company, String phone, String status, String subscriptionStatus, String source, String properties, Instant createdAt, Instant updatedAt) {}
