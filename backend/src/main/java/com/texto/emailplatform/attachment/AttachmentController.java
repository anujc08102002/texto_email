package com.texto.emailplatform.attachment;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/attachments")
@PreAuthorize("@campaignAuthorizationService.canRead()")
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@campaignAuthorizationService.canManage()")
    public ApiResponse<AttachmentService.Document> upload(@RequestPart("file") MultipartFile file){return ApiResponse.ok(service.upload(file));}
    @GetMapping("/{id}") public ApiResponse<AttachmentService.Document> get(@PathVariable UUID id){return ApiResponse.ok(service.get(id));}
    @GetMapping("/{id}/download") public ResponseEntity<byte[]> download(@PathVariable UUID id){
        var doc=service.get(id);var bytes=service.load(TenantContext.get().orElseThrow(),List.of(id.toString())).getFirst().content();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(doc.filename(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
}
