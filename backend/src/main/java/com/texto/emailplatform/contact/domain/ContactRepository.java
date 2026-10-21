package com.texto.emailplatform.contact.domain;
import java.util.Optional; import java.util.UUID; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;
public interface ContactRepository extends JpaRepository<ContactEntity, UUID> {
    Optional<ContactEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ContactEntity> findByTenantIdAndEmail(UUID tenantId, String email);
    @Query("select c from ContactEntity c where c.tenantId=:tenantId and c.status='ACTIVE' and (:q = '' or lower(c.email) like lower(concat('%',:q,'%')) or lower(coalesce(c.firstName,'')) like lower(concat('%',:q,'%')) or lower(coalesce(c.lastName,'')) like lower(concat('%',:q,'%')))")
    Page<ContactEntity> search(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);
}
