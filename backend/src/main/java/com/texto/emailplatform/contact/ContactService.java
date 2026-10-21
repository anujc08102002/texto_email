package com.texto.emailplatform.contact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.contact.api.ContactPageResponse;
import com.texto.emailplatform.contact.api.ContactRequest;
import com.texto.emailplatform.contact.api.ContactResponse;
import com.texto.emailplatform.contact.domain.ContactEntity;
import com.texto.emailplatform.contact.domain.ContactRepository;
import com.texto.emailplatform.suppression.EmailNormalizer;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.UUID;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {
    private final ContactRepository contacts;
    private final ObjectMapper objectMapper;
    public ContactService(ContactRepository contacts, ObjectMapper objectMapper) { this.contacts = contacts; this.objectMapper = objectMapper; }
    @Transactional public ContactResponse create(ContactRequest request) {
        UUID tenantId=tenantId(); String email=EmailNormalizer.normalize(request.email());
        if(contacts.findByTenantIdAndEmail(tenantId,email).isPresent()) throw new ApiException(HttpStatus.CONFLICT.value(),"CONTACT_EXISTS","A contact with this email already exists");
        ContactEntity created=ContactEntity.create(tenantId,email,request.firstName(),request.lastName(),request.company(),request.phone(),request.source(),safeProperties(request.properties()));
        if(request.subscriptionStatus()!=null) created.update(request.firstName(),request.lastName(),request.company(),request.phone(),request.source(),created.getProperties(),request.subscriptionStatus());
        return response(contacts.save(created));
    }
    @Transactional(readOnly=true) public ContactPageResponse list(int page,int size,String q) {
        return list(page,size,q,"createdAt","desc");
    }
    @Transactional(readOnly=true) public ContactPageResponse list(int page,int size,String q,String sort,String direction) {
        int safePage=Math.max(0,page), safeSize=Math.min(100,Math.max(1,size));
        Page<ContactEntity> result=contacts.search(tenantId(), q==null ? "" : q.trim(), PageRequest.of(safePage,safeSize,com.texto.emailplatform.common.PageSort.of(sort,direction,java.util.Set.of("email","firstName","lastName","company","phone","subscriptionStatus","source","createdAt","updatedAt"))));
        return new ContactPageResponse(result.map(this::response).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }
    @Transactional(readOnly=true) public ContactResponse get(UUID id){return response(require(id));}
    @Transactional public ContactResponse update(UUID id,ContactRequest request){ ContactEntity c=require(id); if(!c.getEmail().equals(EmailNormalizer.normalize(request.email()))) throw new ApiException(400,"EMAIL_IMMUTABLE","Email cannot be changed; create a new contact instead"); c.update(request.firstName(),request.lastName(),request.company(),request.phone(),request.source(),safeProperties(request.properties()),request.subscriptionStatus()); return response(c); }
    @Transactional public void delete(UUID id){require(id).archive();}
    private ContactEntity require(UUID id){return contacts.findByIdAndTenantId(id,tenantId()).orElseThrow(()->new ApiException(404,"CONTACT_NOT_FOUND","Contact was not found"));}
    private ContactResponse response(ContactEntity c){try{return new ContactResponse(c.getId(),c.getEmail(),c.getFirstName(),c.getLastName(),c.getCompany(),c.getPhone(),c.getStatus(),c.getSubscriptionStatus(),c.getSource(),objectMapper.writeValueAsString(c.getProperties()),c.getCreatedAt(),c.getUpdatedAt());}catch(Exception exception){throw new ApiException(500,"CONTACT_PROPERTIES_ERROR","Unable to serialize contact properties");}}
    private static UUID tenantId(){return TenantContext.get().orElseThrow(()->new ApiException(401,"UNAUTHENTICATED","Authentication is required"));}
    private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private Map<String,Object> safeProperties(String value){
        if(value==null||value.isBlank())return Map.of();
        try { JsonNode parsed=objectMapper.readTree(value); if(!parsed.isObject()) throw new ApiException(400,"INVALID_PROPERTIES","properties must be a JSON object"); return objectMapper.convertValue(parsed,new TypeReference<Map<String,Object>>(){}); }
        catch(ApiException exception){ throw exception; }
        catch(Exception exception){ throw new ApiException(400,"INVALID_PROPERTIES","properties must be valid JSON"); }
    }
}
