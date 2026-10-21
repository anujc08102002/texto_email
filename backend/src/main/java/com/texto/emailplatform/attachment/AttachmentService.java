package com.texto.emailplatform.attachment;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.delivery.DeliveryEngine.DeliveryAttachment;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    public static final int MAX_COUNT=5;
    public static final long MAX_TOTAL_BYTES=6L*1024*1024;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<ObjectStorageService> storage;
    public AttachmentService(JdbcTemplate jdbc,ObjectProvider<ObjectStorageService> storage){this.jdbc=jdbc;this.storage=storage;}
    private ObjectStorageService store(){var service=storage.getIfAvailable();if(service==null)throw new ApiException(503,"ATTACHMENT_STORAGE_UNAVAILABLE","Private attachment storage is not configured for this environment");return service;}
    private UUID tenant(){return TenantContext.get().orElseThrow(()->new ApiException(401,"UNAUTHENTICATED","Authentication is required"));}
    public record Document(UUID id,String filename,String contentType,long sizeBytes,String checksumSha256,String status){}
    @Transactional public Document upload(MultipartFile file) {
        String filename=file.getOriginalFilename();String type=file.getContentType();
        AttachmentValidationPolicy.validate(filename,type,file.getSize());
        if(filename.indexOf('\\')>=0 || filename.indexOf('/')>=0 || filename.indexOf('\r')>=0 || filename.indexOf('\n')>=0 || type.indexOf('\r')>=0 || type.indexOf('\n')>=0) throw new ApiException(400,"INVALID_ATTACHMENT","Invalid attachment filename or MIME type");
        if(file.getSize()>5L*1024*1024)throw new ApiException(400,"INVALID_ATTACHMENT","A local attachment must be at most 5 MB");
        UUID tenant=tenant(),id=UUID.randomUUID();String key=tenant+"/"+id;
        var target=store();ObjectStorageService.StoredObject object;
        try(var input=file.getInputStream()){object=target.upload(key,type,file.getSize(),input);}catch(java.io.IOException e){throw new ApiException(500,"ATTACHMENT_UPLOAD_FAILED","Unable to read attachment upload");}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            public void afterCompletion(int status){if(status!=STATUS_COMMITTED)target.delete(key);}
        });
        jdbc.update("INSERT INTO attachment_documents (id,tenant_id,original_filename,normalized_filename,content_type,size_bytes,object_key,checksum_sha256,status,scan_detail,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,'AVAILABLE','Malware scanning is not configured',now(),now())",id,tenant,filename,filename.toLowerCase(Locale.ROOT),type,file.getSize(),key,object.checksumSha256());
        return new Document(id,filename,type,file.getSize(),object.checksumSha256(),"AVAILABLE");
    }
    public Document get(UUID owner,UUID id){return jdbc.query("SELECT id,original_filename,content_type,size_bytes,checksum_sha256,status FROM attachment_documents WHERE id=? AND tenant_id=? AND deleted_at IS NULL",(r,n)->new Document(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6)),id,owner).stream().findFirst().orElseThrow(()->new ApiException(404,"ATTACHMENT_NOT_FOUND","Attachment was not found"));}
    public Document get(UUID id){return get(tenant(),id);}
    public List<UUID> ids(Object value) {
        if(value==null)return List.of();
        if(!(value instanceof List<?> list)||list.size()>MAX_COUNT)throw new ApiException(400,"INVALID_ATTACHMENTS","Choose at most five attachments");
        try{return list.stream().map(item->UUID.fromString(item.toString())).distinct().toList();}
        catch(RuntimeException e){throw new ApiException(400,"INVALID_ATTACHMENTS","Attachment IDs must be UUIDs");}
    }
    public void validate(UUID owner,List<UUID> ids) {
        if(ids==null)return;
        if(ids.size()>MAX_COUNT)throw new ApiException(400,"INVALID_ATTACHMENTS","Choose at most five attachments");
        long total=0;
        for(UUID id:ids){var doc=get(owner,id);if(!"AVAILABLE".equals(doc.status()))throw new ApiException(409,"ATTACHMENT_UNAVAILABLE","Attachment is not available for sending");total+=doc.sizeBytes();}
        if(total>MAX_TOTAL_BYTES)throw new ApiException(400,"ATTACHMENTS_TOO_LARGE","Combined attachments must be at most 6 MB");
        if(!ids.isEmpty())store();
    }
    public List<DeliveryAttachment> load(UUID owner,Object references) {
        var ids=ids(references);validate(owner,ids);List<DeliveryAttachment> result=new ArrayList<>();
        for(UUID id:ids){var doc=get(owner,id);try(var input=store().download(owner+"/"+id)){
            byte[] bytes=input.readNBytes(5*1024*1024+1);
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if(bytes.length!=doc.sizeBytes()||!hash.equals(doc.checksumSha256()))throw new IllegalStateException("Private attachment checksum mismatch");
            result.add(new DeliveryAttachment(doc.filename(),doc.contentType(),bytes));
        }catch(Exception e){throw new ApiException(503,"ATTACHMENT_READ_FAILED","Unable to read or verify private attachment");}}
        return result;
    }
}
