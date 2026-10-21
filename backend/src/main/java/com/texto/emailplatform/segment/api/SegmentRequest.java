package com.texto.emailplatform.segment.api;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record SegmentRequest(@NotBlank @Size(max=160) String name, @NotBlank @Size(max=50_000) String definition) {}
