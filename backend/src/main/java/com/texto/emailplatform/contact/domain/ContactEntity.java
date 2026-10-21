package com.texto.emailplatform.contact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contacts")
public class ContactEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String email;
    @Column(name = "first_name") private String firstName;
    @Column(name = "last_name") private String lastName;
    private String company;
    private String phone;
    @Column(nullable = false) private String status;
    @Column(name = "subscription_status", nullable = false) private String subscriptionStatus;
    private String source;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> properties = new LinkedHashMap<>();
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static ContactEntity create(UUID tenantId, String email, String firstName, String lastName, String company, String phone, String source, Map<String, Object> properties) {
        ContactEntity contact = new ContactEntity(); contact.id = UUID.randomUUID(); contact.tenantId = tenantId; contact.email = email;
        contact.firstName = firstName; contact.lastName = lastName; contact.company = company; contact.phone = phone; contact.source = source;
        contact.status = "ACTIVE"; contact.subscriptionStatus = "SUBSCRIBED"; contact.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
        contact.createdAt = contact.updatedAt = Instant.now(); return contact;
    }
    public void update(String firstName, String lastName, String company, String phone, String source, Map<String, Object> properties, String subscriptionStatus) {
        this.firstName=firstName; this.lastName=lastName; this.company=company; this.phone=phone; this.source=source;
        if(properties != null) this.properties=new LinkedHashMap<>(properties); if(subscriptionStatus != null) this.subscriptionStatus=subscriptionStatus; this.updatedAt=Instant.now();
    }
    public void archive() { this.status="ARCHIVED"; this.updatedAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getEmail(){return email;} public String getFirstName(){return firstName;} public String getLastName(){return lastName;} public String getCompany(){return company;} public String getPhone(){return phone;} public String getStatus(){return status;} public String getSubscriptionStatus(){return subscriptionStatus;} public String getSource(){return source;} public Map<String, Object> getProperties(){return properties;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
