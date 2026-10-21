package com.texto.emailplatform.attachment;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/** Private, tenant-authorized object storage boundary. Implementations must never return public URLs. */
public interface ObjectStorageService {
    StoredObject upload(String objectKey, String contentType, long sizeBytes, InputStream content);
    Optional<StoredObject> metadata(String objectKey);
    InputStream download(String objectKey);
    void delete(String objectKey);
    record StoredObject(String objectKey, String contentType, long sizeBytes, String checksumSha256) {}
}
