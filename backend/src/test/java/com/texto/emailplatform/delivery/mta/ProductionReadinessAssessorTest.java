package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@ExtendWith(MockitoExtension.class)
class ProductionReadinessAssessorTest {

    @Mock
    private ProductionDnsProbe dnsProbe;

    @Mock
    private RedisConnectionFactory redis;

    @Mock
    private RedisConnection redisConnection;

    private EmailPlatformProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EmailPlatformProperties();
        properties.getMta().setImplementation("postfix");
        properties.getMta().setHostname("smtp.texto.email");
        properties.getMta().getSmtp().setEhloHostname("smtp.texto.email");
        properties.getMta().setOutboundIp("1.1.1.1");
        properties.getMta().getSmtp().getStarttls().setEnabled(true);
        properties.getMta().getSmtp().getStarttls().setRequired(true);
        properties.getBounce().setDomain("bounce.texto.email");
        properties.getDomains().setDkimKeyEncryptionKey("dGVzdC1rZXk=");
        properties.getDomains().setSpfAuthorizeOutboundIp(true);
    }

    @Test
    void incompleteInfrastructureIsNotProductionReady() {
        ProductionReadinessAssessor assessor = new ProductionReadinessAssessor(properties, dnsProbe, redis);
        ProductionReadinessReport report = assessor.assess(true, true);

        assertThat(report.productionInternetReady()).isFalse();
        assertThat(report.forwardDns()).isEqualTo(ProductionReadinessStatus.BLOCKED);
        assertThat(report.ptr()).isEqualTo(ProductionReadinessStatus.BLOCKED);
        assertThat(report.ptrVerified()).isFalse();
        assertThat(report.bounceMx()).isEqualTo(ProductionReadinessStatus.NOT_CONFIGURED);
        assertThat(report.publicDeliverySwitch()).isEqualTo(ProductionReadinessStatus.NOT_CONFIGURED);
    }

    @Test
    void hostnameIpMismatchIsBlockedNotVerified() {
        when(dnsProbe.lookupForward("smtp.texto.email"))
                .thenReturn(new ProductionDnsProbe.ForwardLookup(true, List.of("9.9.9.9")));
        ProductionReadinessAssessor assessor = new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null);
        ProductionReadinessReport report = assessor.assess(true, true);
        assertThat(report.forwardDns()).isEqualTo(ProductionReadinessStatus.BLOCKED);
        assertThat(report.productionInternetReady()).isFalse();
    }

    @Test
    void dnsLookupFailureIsNotVerified() {
        when(dnsProbe.lookupForward("smtp.texto.email"))
                .thenReturn(ProductionDnsProbe.ForwardLookup.failure());
        when(dnsProbe.lookupPtr("1.1.1.1"))
                .thenReturn(ProductionDnsProbe.PtrLookup.failure());
        ProductionReadinessAssessor assessor = new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null);
        ProductionReadinessReport report = assessor.assess(true, true);
        assertThat(report.forwardDns()).isEqualTo(ProductionReadinessStatus.BLOCKED);
        assertThat(report.ptr()).isEqualTo(ProductionReadinessStatus.BLOCKED);
        assertThat(report.ptrVerified()).isFalse();
    }

    @Test
    void matchingPtrIsVerifiedAndMismatchIsNot() {
        when(dnsProbe.lookupForward("smtp.texto.email"))
                .thenReturn(new ProductionDnsProbe.ForwardLookup(true, List.of("1.1.1.1")));
        when(dnsProbe.lookupPtr("1.1.1.1"))
                .thenReturn(new ProductionDnsProbe.PtrLookup(true, List.of("smtp.texto.email")));
        ProductionReadinessAssessor match = new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null);
        assertThat(match.assess(true, true).ptr()).isEqualTo(ProductionReadinessStatus.VERIFIED);
        assertThat(match.assess(true, true).ptrVerified()).isTrue();

        when(dnsProbe.lookupPtr("1.1.1.1"))
                .thenReturn(new ProductionDnsProbe.PtrLookup(true, List.of("other.example.net")));
        ProductionReadinessAssessor mismatch = new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null);
        ProductionReadinessReport report = mismatch.assess(true, true);
        assertThat(report.ptr()).isEqualTo(ProductionReadinessStatus.BLOCKED);
        assertThat(report.ptrVerified()).isFalse();
        assertThat(report.productionInternetReady()).isFalse();
    }

    @Test
    void bounceMxLookupFailureIsNotVerified() {
        properties.getBounce().setMxHostname("inbound-bounce.texto.email");
        when(dnsProbe.lookupMx("inbound-bounce.texto.email"))
                .thenReturn(ProductionDnsProbe.MxLookup.failure());
        ProductionReadinessAssessor assessor = new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null);
        assertThat(assessor.assess(true, true).bounceMx()).isEqualTo(ProductionReadinessStatus.BLOCKED);
    }

    @Test
    void fullyVerifiedStillRequiresPublicSwitchAndDoesNotFakeInternetReadyWithoutIt() {
        properties.getBounce().setMxHostname("inbound-bounce.texto.email");
        when(dnsProbe.lookupForward("smtp.texto.email"))
                .thenReturn(new ProductionDnsProbe.ForwardLookup(true, List.of("1.1.1.1")));
        when(dnsProbe.lookupPtr("1.1.1.1"))
                .thenReturn(new ProductionDnsProbe.PtrLookup(true, List.of("smtp.texto.email")));
        when(dnsProbe.lookupMx("inbound-bounce.texto.email"))
                .thenReturn(new ProductionDnsProbe.MxLookup(true, List.of("mx.texto.email")));
        when(redis.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        ProductionReadinessAssessor assessor = new ProductionReadinessAssessor(properties, dnsProbe, redis);
        ProductionReadinessReport off = assessor.assess(true, true);
        assertThat(off.forwardDns()).isEqualTo(ProductionReadinessStatus.VERIFIED);
        assertThat(off.ptrVerified()).isTrue();
        assertThat(off.bounceMx()).isEqualTo(ProductionReadinessStatus.VERIFIED);
        assertThat(off.rateLimiter()).isEqualTo(ProductionReadinessStatus.VERIFIED);
        assertThat(off.productionInternetReady()).isFalse();

        properties.getMta().setPublicDeliveryEnabled(true);
        ProductionReadinessReport on = new ProductionReadinessAssessor(properties, dnsProbe, redis).assess(true, true);
        assertThat(on.publicDeliverySwitch()).isEqualTo(ProductionReadinessStatus.CONFIGURED);
        assertThat(on.productionInternetReady()).isTrue();
    }

    @Test
    void spfIsConfiguredNotPassWhenOutboundIpAuthorized() {
        ProductionReadinessAssessor assessor = new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null);
        assertThat(assessor.assess(true, true).spfAuthorization()).isEqualTo(ProductionReadinessStatus.CONFIGURED);
        properties.getDomains().setSpfAuthorizeOutboundIp(false);
        assertThat(new ProductionReadinessAssessor(properties, dnsProbe, (RedisConnectionFactory) null).assess(true, true).spfAuthorization())
                .isEqualTo(ProductionReadinessStatus.NOT_CONFIGURED);
    }
}
