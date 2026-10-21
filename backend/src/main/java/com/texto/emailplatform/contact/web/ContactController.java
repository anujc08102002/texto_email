package com.texto.emailplatform.contact.web;
import com.texto.emailplatform.common.api.ApiResponse; import com.texto.emailplatform.contact.ContactImportService; import com.texto.emailplatform.contact.ContactService; import com.texto.emailplatform.contact.api.*; import jakarta.validation.Valid; import java.util.UUID; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
@org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canRead()")
@RestController @RequestMapping("/api/v1/contacts")
public class ContactController {
    private final ContactService service; private final ContactImportService imports; public ContactController(ContactService service,ContactImportService imports){this.service=service;this.imports=imports;}
    @org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canManage()") @PostMapping public ApiResponse<ContactResponse> create(@Valid @RequestBody ContactRequest request){return ApiResponse.ok(service.create(request));}
    @GetMapping public ApiResponse<ContactPageResponse> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String q,@RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="desc") String direction){return ApiResponse.ok(service.list(page,size,q,sort,direction));}
    @org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canManage()") @PostMapping(value="/import/csv",consumes="multipart/form-data") public ApiResponse<ContactImportResponse> importCsv(@RequestPart("file") MultipartFile file){return ApiResponse.ok(imports.importCsv(file));}
    @GetMapping("/{id}") public ApiResponse<ContactResponse> get(@PathVariable UUID id){return ApiResponse.ok(service.get(id));}
    @org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canManage()") @PatchMapping("/{id}") public ApiResponse<ContactResponse> update(@PathVariable UUID id,@Valid @RequestBody ContactRequest request){return ApiResponse.ok(service.update(id,request));}
    @org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canManage()") @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable UUID id){service.delete(id); return ApiResponse.ok(null);}
}
