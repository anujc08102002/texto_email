package com.texto.emailplatform.contact.api;
import java.util.List;
public record ContactPageResponse(List<ContactResponse> items, int page, int size, long totalElements, int totalPages) {}
