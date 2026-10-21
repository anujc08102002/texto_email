package com.texto.emailplatform.campaign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.campaign.domain.*;
import com.texto.emailplatform.contact.ContactImportService;
import com.texto.emailplatform.segment.SegmentConditionQueryBuilder;
import com.texto.emailplatform.segment.domain.*;
import com.texto.emailplatform.tenant.web.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Run against a dedicated local database: -Dcampaign.database.tests=true -Dspring.datasource.url=... */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named="campaign.database.tests", matches="true")
@Import({CampaignAudienceService.class, SegmentConditionQueryBuilder.class, ObjectMapper.class,
        CampaignUnsubscribeService.class, ContactImportService.class, CampaignDispatchService.class,
        CampaignRecipientSender.class, CampaignPersonalizer.class, CampaignReportExportService.class, com.texto.emailplatform.contact.ContactService.class})
class CampaignDatabaseIT {
    @Autowired com.texto.emailplatform.contact.ContactService contactService;
    @Autowired CampaignAudienceService audiences;
    @Autowired CampaignRepository campaigns;
    @Autowired com.texto.emailplatform.contact.domain.ContactRepository contacts;
    @Autowired SegmentRepository segments;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CampaignUnsubscribeService unsubscribe;
    @Autowired ContactImportService imports;
    @MockitoBean CampaignAuthorizationService authorization;
    @MockitoBean com.texto.emailplatform.email.EmailService emails;
    @MockitoBean com.texto.emailplatform.template.TemplateService templates;
    @Autowired CampaignDispatchService dispatch;
    @Autowired CampaignReportExportService exports;
    @Autowired com.texto.emailplatform.email.domain.EmailMessageRepository messages;

    UUID tenant, otherTenant, campaignId;

    @BeforeEach void seed() {
        tenant = UUID.randomUUID(); otherTenant = UUID.randomUUID();
        for (UUID id : List.of(tenant, otherTenant)) {
            jdbc.update("INSERT INTO tenants (id, name, slug, status, created_at, updated_at) VALUES (?, 'Test', ?, 'ACTIVE', now(), now())", id, "test-" + id);
        }
        TenantContext.set(tenant);
        var campaign = CampaignEntity.draft(tenant, "Bulk test", "sender@example.com", null, "Hello {{first_name}}", null, "<p>Hello</p>", null, null, null);
        campaignId = campaigns.saveAndFlush(campaign).getId();
    }
    @AfterEach void clear() { TenantContext.clear(); }

    UUID contact(UUID owner, String email, String company, String subscription) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO contacts (id, tenant_id, email, company, status, subscription_status, properties, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, '{\"spent\":1200}', now(), now())", id, owner, email, company, subscription);
        return id;
    }

    @Test void segmentAudienceIsTenantScopedDeduplicatedAndExcludesUnsubscribedAndSuppressed() {
        contact(tenant, "yes@example.com", "Acme", "SUBSCRIBED");
        contact(tenant, "no@example.com", "Other", "SUBSCRIBED");
        contact(tenant, "unsubscribed@example.com", "Acme", "UNSUBSCRIBED");
        contact(otherTenant, "other@example.com", "Acme", "SUBSCRIBED");
        contact(tenant, "suppressed@example.com", "Acme", "SUBSCRIBED");
        jdbc.update("INSERT INTO suppressions (id, tenant_id, email, normalized_email, reason, type, source, created_at, updated_at) VALUES (?, ?, 'suppressed@example.com', 'suppressed@example.com', 'manual', 'MANUAL', 'USER', now(), now())", UUID.randomUUID(), tenant);
        var segment = segments.saveAndFlush(SegmentEntity.create(tenant, "Acme", "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"company\",\"operator\":\"EQUALS\",\"value\":\"Acme\"}]}"));
        assertThat(audiences.add(campaignId, new CampaignAudienceService.AudienceRequest(segment.getId(), false)).added()).isEqualTo(1);
        assertThat(audiences.add(campaignId, new CampaignAudienceService.AudienceRequest(segment.getId(), false)).added()).isZero();
        assertThat(jdbc.queryForList("SELECT email FROM campaign_recipients WHERE campaign_id = ?", String.class, campaignId)).containsExactly("yes@example.com");
    }

    @Test void contactSearchSupportsAnEmptyQueryAndHidesArchivedContacts() {
        UUID first = contact(tenant, "search@example.test", "Acme", "SUBSCRIBED");
        UUID archived = contact(tenant, "archived@example.test", "Acme", "SUBSCRIBED");
        jdbc.update("UPDATE contacts SET status = 'ARCHIVED' WHERE id = ?", archived);
        assertThat(contacts.search(tenant, "", org.springframework.data.domain.PageRequest.of(0, 20)).getContent())
                .extracting(com.texto.emailplatform.contact.domain.ContactEntity::getId).containsExactly(first);
        assertThat(contacts.search(tenant, "search", org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test void serverContactSortingPagesAcrossTheWholeTenantAndRejectsUntrustedFields() {
        contact(tenant, "z@example.test", "Acme", "SUBSCRIBED");
        contact(tenant, "a@example.test", "Acme", "SUBSCRIBED");
        contact(tenant, "m@example.test", "Acme", "SUBSCRIBED");
        contact(otherTenant, "foreign@example.test", "Acme", "SUBSCRIBED");
        var first = contactService.list(0, 2, "", "email", "asc");
        var next = contactService.list(1, 2, "", "email", "asc");
        assertThat(first.items()).extracting(item -> item.email()).containsExactly("a@example.test", "m@example.test");
        assertThat(next.items()).extracting(item -> item.email()).containsExactly("z@example.test");
        assertThat(first.totalElements()).isEqualTo(3);
        assertThatThrownBy(() -> contactService.list(0, 20, "", "lower(email)", "asc"))
                .hasMessageContaining("supported sort");
        assertThatThrownBy(() -> contactService.list(0, 20, "", "email", "anything"))
                .hasMessageContaining("supported sort");
    }

    @Test void campaignSortingWorksWithSearchAndTenantIsolation() {
        campaigns.saveAndFlush(CampaignEntity.draft(tenant, "A launch", "sender@example.test", null, "Hi", null, "<p>Hello</p>", null, null, null));
        campaigns.saveAndFlush(CampaignEntity.draft(otherTenant, "A foreign", "sender@example.test", null, "Hi", null, "<p>Hello</p>", null, null, null));
        var result = campaigns.searchByTenant(tenant, "DRAFT", "", org.springframework.data.domain.PageRequest.of(0, 1,
                com.texto.emailplatform.common.PageSort.of("name", "asc", Set.of("name"))));
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(CampaignEntity::getName).containsExactly("A launch");
    }

    @Test void privateAttachmentUploadEnforcesTenantOwnershipAndPreservesBytes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var store=new com.texto.emailplatform.attachment.LocalObjectStorageService(directory.toString());
        var provider=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        var service=new com.texto.emailplatform.attachment.AttachmentService(jdbc,provider);
        byte[] bytes="Local attachment verification".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var doc=service.upload(new MockMultipartFile("file","receipt.txt","text/plain",bytes));
        assertThat(service.get(tenant,doc.id()).status()).isEqualTo("AVAILABLE");
        assertThat(service.load(tenant,List.of(doc.id().toString())).getFirst().content()).isEqualTo(bytes);
        assertThatThrownBy(()->service.get(otherTenant,doc.id())).hasMessageContaining("was not found");
        assertThatThrownBy(()->service.upload(new MockMultipartFile("file","../receipt.txt","text/plain",bytes))).hasMessageContaining("Invalid attachment");
        java.nio.file.Files.write(directory.resolve(tenant+"/"+doc.id()),"tampered".getBytes());
        assertThatThrownBy(()->service.load(tenant,List.of(doc.id().toString()))).hasMessageContaining("Unable to read or verify");
    }

    @Test void rejectsForeignSegmentsAndCampaigns() {
        var foreign = segments.saveAndFlush(SegmentEntity.create(otherTenant, "Private", "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"email\",\"operator\":\"IS_NOT_EMPTY\"}]}"));
        assertThatThrownBy(() -> audiences.add(campaignId, new CampaignAudienceService.AudienceRequest(foreign.getId(), false))).hasMessageContaining("Segment was not found");
    }

    @Test void oneClickIsIdempotentAndExcludesAddressFromFutureAudiences() {
        contact(tenant, "recipient@example.com", "Acme", "SUBSCRIBED");
        String url = unsubscribe.urlFor(tenant, "recipient@example.com");
        String token = url.substring(url.lastIndexOf('/') + 1);
        unsubscribe.unsubscribe(token); unsubscribe.unsubscribe(token);
        assertThat(jdbc.queryForObject("SELECT subscription_status FROM contacts WHERE tenant_id = ?", String.class, tenant)).isEqualTo("UNSUBSCRIBED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suppressions WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(1);
        assertThat(audiences.add(campaignId, new CampaignAudienceService.AudienceRequest(null, true)).added()).isZero();
    }

    @Test void importsMultilineCsvAndCustomPropertiesAndSkipsExistingContacts() {
        var file = new MockMultipartFile("file", "contacts.csv", "text/csv", ("\uFEFFemail,first_name,company,custom.city\r\na@example.com,Ada,\"Acme\nCompany\",Mumbai\r\na@example.com,Duplicate,Acme,Pune\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = imports.importCsv(file);
        assertThat(result.accepted()).isEqualTo(1); assertThat(result.skipped()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT properties->>'city' FROM contacts WHERE tenant_id = ?", String.class, tenant)).isEqualTo("Mumbai");
        assertThat(jdbc.queryForObject("SELECT company FROM contacts WHERE tenant_id = ?", String.class, tenant)).isEqualTo("Acme\nCompany");
        assertThat(imports.importCsv(file).accepted()).isZero();
    }

    @Test void expiredDispatchLeaseCanBeReclaimedButLiveClaimCannot() {
        jdbc.update("UPDATE campaigns SET status = 'QUEUED' WHERE id = ?", campaignId);
        var now = java.time.Instant.now();
        assertThat(campaigns.claimForDispatch(campaignId, now, UUID.randomUUID(), now.plusSeconds(300))).isEqualTo(1);
        assertThat(campaigns.claimForDispatch(campaignId, now, UUID.randomUUID(), now.plusSeconds(300))).isZero();
        jdbc.update("UPDATE campaigns SET dispatch_lease_until = now() - interval '1 second' WHERE id = ?", campaignId);
        assertThat(campaigns.claimForDispatch(campaignId, now, UUID.randomUUID(), now.plusSeconds(300))).isEqualTo(1);
    }

    @Test void streamsRecipientReportWithCapturedTenantScope() throws Exception {
        contact(tenant, "export@example.test", "Acme", "SUBSCRIBED");
        contact(otherTenant, "private@example.test", "Acme", "SUBSCRIBED");
        audiences.add(campaignId, new CampaignAudienceService.AudienceRequest(null, true));
        var stream = exports.export(campaignId);
        TenantContext.clear();
        var output = new java.io.ByteArrayOutputStream();
        stream.writeTo(output);
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("email,status,error,added_at", "export@example.test", "PENDING").doesNotContain("private@example.test");
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void dispatchCommitsClaimBeforeRecipientTransactionsAndCannotQueueRecipientTwice() {
        UUID creator = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash, display_name, role, status, created_at, updated_at) VALUES (?, ?, ?, 'test-only', 'Tester', 'OWNER', 'ACTIVE', now(), now())",
                creator, tenant, creator + "@example.test");
        jdbc.update("UPDATE campaigns SET created_by = ? WHERE id = ?", creator, campaignId);
        contact(tenant, "dispatch@example.test", "Acme", "SUBSCRIBED");
        audiences.add(campaignId, new CampaignAudienceService.AudienceRequest(null, true));
        jdbc.update("UPDATE campaigns SET status = 'QUEUED' WHERE id = ?", campaignId);
        when(emails.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            var request = (com.texto.emailplatform.email.api.SendEmailRequest) call.getArgument(0);
            var message = messages.saveAndFlush(com.texto.emailplatform.email.domain.EmailMessageEntity.create(tenant,
                    request.from(), request.to(), List.of(), List.of(), List.of(), null, request.subject(), request.html(),
                    request.text(), null, null, call.getArgument(1), request.metadata(), 5, creator));
            var response = mock(com.texto.emailplatform.email.api.EmailMessageResponse.class);
            when(response.id()).thenReturn(message.getId()); when(response.status()).thenReturn("QUEUED");
            return response;
        });
        assertThatCode(() -> dispatch.dispatch(campaignId)).doesNotThrowAnyException();
        dispatch.dispatch(campaignId);
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campaign_recipients WHERE campaign_id = ? AND status = 'ACCEPTED'", Integer.class, campaignId)).isEqualTo(1);
        verify(emails, times(1)).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.startsWith("campaign:"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campaign_unsubscribe_tokens WHERE tenant_id = ?", Integer.class, tenant)).isEqualTo(1);
    }
}
