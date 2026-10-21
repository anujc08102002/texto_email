package com.texto.emailplatform.attachment;

import com.texto.emailplatform.common.exception.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Allow-list validation used before bytes enter private object storage or a delivery queue. */
public final class AttachmentValidationPolicy {
    public static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf","doc","docx","xls","xlsx","csv","jpg","jpeg","png","gif","txt","zip");
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe","dll","bat","cmd","com","msi","js","vbs","ps1","sh","jar","php");
    private AttachmentValidationPolicy() {}
    public static void validate(String filename,String contentType,long sizeBytes) {
        if(filename==null||filename.isBlank()||filename.length()>512) reject("Invalid attachment filename");
        if(sizeBytes<0||sizeBytes>MAX_ATTACHMENT_BYTES) reject("Attachment exceeds the 10 MB limit");
        String extension=extension(filename);
        if(BLOCKED_EXTENSIONS.contains(extension)||!ALLOWED_EXTENSIONS.contains(extension)) reject("Attachment type is not allowed");
        if(contentType==null||contentType.isBlank()||contentType.length()>255) reject("Invalid attachment MIME type");
    }
    private static String extension(String filename){int index=filename.lastIndexOf('.');return index<1?"":filename.substring(index+1).toLowerCase(Locale.ROOT);}
    private static void reject(String message){throw new ApiException(HttpStatus.BAD_REQUEST.value(),"INVALID_ATTACHMENT",message);}
}
