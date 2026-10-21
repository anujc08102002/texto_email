package com.texto.emailplatform.attachment;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Private filesystem implementation; production wiring requires an explicit persistent root. */
@Component @Profile("local")
public class LocalObjectStorageService implements ObjectStorageService {
    private final Path root;
    public LocalObjectStorageService(@Value("${email-platform.attachments.local-root:target/local-attachments}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    private Path resolve(String key) {
        if (!key.matches("[a-f0-9-]{36}/[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid private object key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid private object key");
        return path;
    }
    public StoredObject upload(String key,String type,long size,InputStream content) {
        Path file=resolve(key);
        try {
            byte[] bytes=content.readNBytes((int)AttachmentValidationPolicy.MAX_ATTACHMENT_BYTES+1);
            if(bytes.length!=size || bytes.length>AttachmentValidationPolicy.MAX_ATTACHMENT_BYTES) throw new IOException("Invalid attachment size");
            Files.createDirectories(file.getParent());
            Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
            return new StoredObject(key,type,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch(Exception e){ throw new IllegalStateException("Unable to store private attachment",e); }
    }
    public Optional<StoredObject> metadata(String key) {
        try { Path file=resolve(key);return Files.exists(file)?Optional.of(new StoredObject(key,"application/octet-stream",Files.size(file),null)):Optional.empty(); }
        catch(IOException e){throw new IllegalStateException("Unable to read private attachment",e);}
    }
    public InputStream download(String key) {
        try{return Files.newInputStream(resolve(key));}catch(IOException e){throw new IllegalStateException("Unable to read private attachment",e);}
    }
    public void delete(String key){try{Files.deleteIfExists(resolve(key));}catch(IOException e){throw new IllegalStateException("Unable to remove private attachment",e);}}
}
