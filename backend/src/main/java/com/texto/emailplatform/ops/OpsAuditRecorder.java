package com.texto.emailplatform.ops;

import com.texto.emailplatform.ops.domain.PlatformAuditEventEntity;
import com.texto.emailplatform.ops.domain.PlatformAuditEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsAuditRecorder {

    private final PlatformAuditEventRepository auditEventRepository;

    public OpsAuditRecorder(PlatformAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID adminId,
            String method,
            String path,
            String queryString,
            Integer statusCode,
            Integer durationMs,
            String requestId,
            String remoteAddress
    ) {
        auditEventRepository.save(PlatformAuditEventEntity.create(
                adminId,
                method,
                path,
                queryString,
                statusCode,
                durationMs,
                requestId,
                remoteAddress
        ));
    }
}
