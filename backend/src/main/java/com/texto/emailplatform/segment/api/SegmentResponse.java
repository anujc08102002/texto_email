package com.texto.emailplatform.segment.api;
import java.time.Instant; import java.util.UUID;
public record SegmentResponse(UUID id,String name,String definition,Instant createdAt,Instant updatedAt){}
