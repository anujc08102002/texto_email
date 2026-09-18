package com.texto.emailplatform.domain.dns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.naming.CommunicationException;
import javax.naming.InvalidNameException;
import javax.naming.NameNotFoundException;
import javax.naming.ServiceUnavailableException;
import javax.naming.TimeLimitExceededException;
import org.junit.jupiter.api.Test;

class DomainDnsAuthenticationTest {

    @Test
    void ownershipIsDedicatedAndDoesNotUseFunctionalRecords() {
        String value = DomainOwnershipRecord.generate("abc123");
        assertThat(value).isEqualTo("texto-domain-verification=abc123");
        assertThat(DomainOwnershipRecord.ownerName("mail.example.com")).isEqualTo("texto-verify.mail.example.com");
        assertThat(DomainOwnershipRecord.match(List.of(value), "abc123").isMatched()).isTrue();
        assertThat(DomainOwnershipRecord.match(List.of(value), "nope").isMatched()).isFalse();
        assertThat(DomainOwnershipRecord.match(List.of(), "abc123").status()).isEqualTo(DomainOwnershipRecord.Status.MISSING);
    }

    @Test
    void spfIsExactlyOneRecordWithConfiguredInclude() {
        String generated = SpfRecord.generate("_spf.texto.email", "~");
        assertThat(generated).isEqualTo("v=spf1 include:_spf.texto.email ~all");
        assertThat(generated).doesNotContain("texto-verify");
        assertThat(SpfRecord.match(List.of(generated), "_spf.texto.email", "~").isMatched()).isTrue();
        assertThat(SpfRecord.match(List.of("\"v=spf1\" \" include:_spf.texto.email ~all\""), "_spf.texto.email", "~").isMatched()).isTrue();
        assertThat(SpfRecord.match(List.of(generated, "v=spf1 include:else.example ~all"), "_spf.texto.email", "~").status())
                .isEqualTo(SpfRecord.Status.MULTIPLE);
        assertThat(SpfRecord.match(List.of("v=spf1 include:_spf.texto.email ~all extra"), "_spf.texto.email", "~").isMatched()).isFalse();
        assertThat(SpfRecord.match(List.of("google-site-verification=abc"), "_spf.texto.email", "~").status())
                .isEqualTo(SpfRecord.Status.MISSING);
        assertThat(SpfRecord.match(List.of("google-site-verification=abc", generated), "_spf.texto.email", "~").isMatched()).isTrue();
        assertThat(SpfRecord.match(List.of("v=spf10 include:_spf.texto.email ~all"), "_spf.texto.email", "~").isMatched()).isFalse();
        assertThat(SpfRecord.generate("_spf.texto.email", "~", "1.1.1.1")).isEqualTo("v=spf1 ip4:1.1.1.1 ~all");
        assertThat(SpfRecord.match(List.of("v=spf1 ip4:1.1.1.1 ~all"), "_spf.texto.email", "~", "1.1.1.1").isMatched()).isTrue();
        assertThat(SpfRecord.match(List.of("v=spf1 ip4:1.1.1.1 ~all"), "_spf.texto.email", "~").isMatched()).isFalse();
    }

    @Test
    void dkimJoinsTxtChunksAndRequiresMatchingPublicKey() {
        String key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA";
        String generated = DkimDnsRecord.generate(key);
        assertThat(generated).isEqualTo("v=DKIM1; k=rsa; p=" + key);
        assertThat(generated).doesNotContain("texto-verify");
        assertThat(DkimDnsRecord.ownerName("texto", "mail.example.com")).isEqualTo("texto._domainkey.mail.example.com");
        assertThat(DkimDnsRecord.match(List.of(generated), key).isMatched()).isTrue();
        assertThat(DkimDnsRecord.match(List.of("\"v=DKIM1; k=rsa; p=\" \"" + key + "\""), key).isMatched()).isTrue();
        assertThat(DkimDnsRecord.match(List.of("v=DKIM1; k=rsa; p=AAAA"), key).isMatched()).isFalse();
        assertThat(DkimDnsRecord.match(List.of("v=DKIM1; k=rsa; p="), key).status()).isEqualTo(DkimDnsRecord.Status.REVOKED);
    }

    @Test
    void dmarcDefaultsToNoneAndRejectsMalformedPolicy() {
        String generated = DmarcRecord.generate(DmarcSettings.defaults());
        assertThat(generated).isEqualTo("v=DMARC1; p=none");
        assertThat(generated).doesNotContain("texto-verify");
        assertThat(DmarcRecord.match(List.of(generated), DmarcSettings.defaults()).isMatched()).isTrue();
        assertThat(DmarcRecord.match(List.of("v=DMARC1; p=none; rua=mailto:dmarc@example.com"), DmarcSettings.defaults()).isMatched()).isTrue();
        assertThat(DmarcRecord.parse("v=DMARC1; pct=100")).isNull();
        assertThat(DmarcRecord.match(List.of("v=DMARC1; pct=100"), DmarcSettings.defaults()).status())
                .isEqualTo(DmarcRecord.Status.MALFORMED);
        assertThat(DmarcRecord.match(List.of("v=DMARC1; p=reject"), DmarcSettings.defaults()).isMatched()).isFalse();
        assertThat(DmarcRecord.parse("v=DMARC1; p=none; adkim=r").tags().get("adkim")).isEqualTo("r");
    }

    @Test
    void txtParserConcatenatesCharacterStringsWithoutAddingSpaces() {
        assertThat(DnsTxtRecordParser.reconstruct("\"v=DKIM1; k=rsa; p=\" \"MIIB\"")).isEqualTo("v=DKIM1; k=rsa; p=MIIB");
        assertThat(DnsTxtRecordParser.reconstruct("plain")).isEqualTo("plain");
    }

    @Test
    void reconstructsMixedQuotedAndUnquotedTxtChunks() {
        String raw =
            "\"v=DKIM1; k=rsa; p=ABCDEF\" "
                    + "GHIJKLMN";

        assertThat(DnsTxtRecordParser.reconstruct(raw))
            .isEqualTo("v=DKIM1; k=rsa; p=ABCDEFGHIJKLMN");
    }

    @Test
    void dnsErrorsAreClassifiedWithoutResolverDetails() {
        assertThat(DnsErrorClassifier.classify(new NameNotFoundException("x"))).isEqualTo(DnsLookupOutcome.NXDOMAIN);
        assertThat(DnsErrorClassifier.classify(new TimeLimitExceededException("x"))).isEqualTo(DnsLookupOutcome.TIMEOUT);
        assertThat(DnsErrorClassifier.classify(new CommunicationException("timed out"))).isEqualTo(DnsLookupOutcome.TIMEOUT);
        assertThat(DnsErrorClassifier.classify(new CommunicationException("connection reset")))
                .isEqualTo(DnsLookupOutcome.TEMPORARY_FAILURE);
        assertThat(DnsErrorClassifier.classify(new ServiceUnavailableException("x")))
                .isEqualTo(DnsLookupOutcome.TEMPORARY_FAILURE);
        assertThat(DnsErrorClassifier.classify(new InvalidNameException("x"))).isEqualTo(DnsLookupOutcome.MALFORMED);
        assertThat(DnsErrorClassifier.customerMessage(DnsLookupOutcome.TIMEOUT)).doesNotContain("dns:");
        assertThat(DnsErrorClassifier.customerMessage(DnsLookupOutcome.NXDOMAIN)).doesNotContain("javax.naming");
        assertThat(DnsErrorClassifier.customerMessage(DnsLookupOutcome.TEMPORARY_FAILURE)).doesNotContain("javax.naming");
    }
}
