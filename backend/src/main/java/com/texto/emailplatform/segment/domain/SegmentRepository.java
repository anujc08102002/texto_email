package com.texto.emailplatform.segment.domain;
import java.util.List; import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface SegmentRepository extends JpaRepository<SegmentEntity,UUID>{ List<SegmentEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId); Optional<SegmentEntity> findByIdAndTenantId(UUID id,UUID tenantId); boolean existsByTenantIdAndName(UUID tenantId,String name); }
