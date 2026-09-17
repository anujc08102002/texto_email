package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Production infrastructure readiness. DNS/PTR/MX are diagnostic lookups and are never
 * inferred from configuration alone. Lookup failure is {@code BLOCKED}, not {@code VERIFIED}.
 */
@Component
public class ProductionReadinessAssessor {

    private static final Duration DNS_CACHE_TTL = Duration.ofSeconds(30);

    private final EmailPlatformProperties properties;
    private final ProductionDnsProbe dnsProbe;
    private final RedisConnectionFactory redis;
    private final Object dnsLock = new Object();
    private CachedDns cachedDns;

    @Autowired
    public ProductionReadinessAssessor(
            EmailPlatformProperties properties,
            ProductionDnsProbe dnsProbe,
            ObjectProvider<RedisConnectionFactory> redis
    ) {
        this(properties, dnsProbe, redis.getIfAvailable());
    }

    ProductionReadinessAssessor(
            EmailPlatformProperties properties,
            ProductionDnsProbe dnsProbe,
            RedisConnectionFactory redis
    ) {
        this.properties = properties;
        this.dnsProbe = dnsProbe;
        this.redis = redis;
    }

    public ProductionReadinessReport assess(boolean mtaReachable, boolean production) {
        String hostname = ProductionIdentity.normalize(MtaPropertiesValidator.smtpIdentity(properties));
        String outboundIp = properties.getMta().getOutboundIp() == null ? "" : properties.getMta().getOutboundIp().trim();
        String bounceDomain = ProductionIdentity.normalize(properties.getBounce().getDomain());
        String bounceMx = ProductionIdentity.normalize(properties.getBounce().getMxHostname());

        DnsSnapshot dns = dnsSnapshot(hostname, outboundIp, bounceMx);

        ProductionReadinessStatus forwardDns = forwardStatus(hostname, outboundIp, dns.forward());
        ProductionReadinessStatus ptr = ptrStatus(hostname, outboundIp, dns.ptr());
        ProductionReadinessStatus bounceMxStatus = bounceMxStatus(bounceMx, dns.mx());
        ProductionReadinessStatus bounce = bounceDomainStatus(bounceDomain, production);
        ProductionReadinessStatus tls = tlsStatus(production);
        ProductionReadinessStatus dkim = dkimStatus();
        ProductionReadinessStatus rateLimiter = rateLimiterStatus();
        ProductionReadinessStatus publicSwitch = properties.getMta().isPublicDeliveryEnabled()
                ? ProductionReadinessStatus.CONFIGURED
                : ProductionReadinessStatus.NOT_CONFIGURED;
        ProductionReadinessStatus productionConfig = productionConfigStatus(production, hostname, outboundIp, bounceDomain, tls);
        ProductionReadinessStatus spf = spfStatus(outboundIp);

        boolean internetReady = mtaReachable
                && production
                && productionConfig == ProductionReadinessStatus.CONFIGURED
                && forwardDns == ProductionReadinessStatus.VERIFIED
                && ptr == ProductionReadinessStatus.VERIFIED
                && dkim == ProductionReadinessStatus.CONFIGURED
                && bounce == ProductionReadinessStatus.CONFIGURED
                && bounceMxStatus == ProductionReadinessStatus.VERIFIED
                && tls == ProductionReadinessStatus.CONFIGURED
                && rateLimiter == ProductionReadinessStatus.VERIFIED
                && publicSwitch == ProductionReadinessStatus.CONFIGURED
                && spf == ProductionReadinessStatus.CONFIGURED;

        return new ProductionReadinessReport(
                internetReady,
                ProductionReadinessStatus.CONFIGURED,
                mtaReachable ? ProductionReadinessStatus.VERIFIED : ProductionReadinessStatus.BLOCKED,
                productionConfig,
                forwardDns,
                ptr,
                dkim,
                bounce,
                bounceMxStatus,
                tls,
                rateLimiter,
                publicSwitch,
                spf
        );
    }

    private ProductionReadinessStatus forwardStatus(
            String hostname,
            String outboundIp,
            ProductionDnsProbe.ForwardLookup lookup
    ) {
        if (hostname.isEmpty()) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        if (!ProductionIdentity.isProductionHostname(hostname)) {
            return ProductionReadinessStatus.BLOCKED;
        }
        if (!ProductionIdentity.isPublicIpv4(outboundIp)) {
            return ProductionReadinessStatus.CONFIGURED;
        }
        if (lookup == null || !lookup.resolved() || lookup.ipv4Addresses().isEmpty()) {
            return ProductionReadinessStatus.BLOCKED;
        }
        boolean match = lookup.ipv4Addresses().stream().anyMatch(address -> outboundIp.equals(address));
        return match ? ProductionReadinessStatus.VERIFIED : ProductionReadinessStatus.BLOCKED;
    }

    private ProductionReadinessStatus ptrStatus(
            String hostname,
            String outboundIp,
            ProductionDnsProbe.PtrLookup lookup
    ) {
        if (outboundIp.isEmpty()) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        if (!ProductionIdentity.isPublicIpv4(outboundIp)) {
            return ProductionReadinessStatus.BLOCKED;
        }
        if (hostname.isEmpty() || !ProductionIdentity.isProductionHostname(hostname)) {
            return ProductionReadinessStatus.CONFIGURED;
        }
        if (lookup == null || !lookup.resolved() || lookup.names().isEmpty()) {
            return ProductionReadinessStatus.BLOCKED;
        }
        boolean match = lookup.names().stream().anyMatch(name -> hostname.equals(ProductionIdentity.normalize(name)));
        return match ? ProductionReadinessStatus.VERIFIED : ProductionReadinessStatus.BLOCKED;
    }

    private ProductionReadinessStatus bounceMxStatus(String bounceMx, ProductionDnsProbe.MxLookup lookup) {
        if (bounceMx.isEmpty()) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        if (!ProductionIdentity.isProductionHostname(bounceMx)) {
            return ProductionReadinessStatus.BLOCKED;
        }
        if (lookup == null || !lookup.resolved() || lookup.hosts().isEmpty()) {
            return ProductionReadinessStatus.BLOCKED;
        }
        return ProductionReadinessStatus.VERIFIED;
    }

    private ProductionReadinessStatus bounceDomainStatus(String bounceDomain, boolean production) {
        if (bounceDomain.isEmpty()) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        if (!ProductionIdentity.isProductionBounceDomain(bounceDomain)) {
            return production ? ProductionReadinessStatus.BLOCKED : ProductionReadinessStatus.CONFIGURED;
        }
        return ProductionReadinessStatus.CONFIGURED;
    }

    private ProductionReadinessStatus tlsStatus(boolean production) {
        var smtp = properties.getMta().getSmtp();
        boolean configured = smtp.getSsl().isEnabled()
                || (smtp.getStarttls().isEnabled() && smtp.getStarttls().isRequired());
        if (configured) {
            return ProductionReadinessStatus.CONFIGURED;
        }
        return production ? ProductionReadinessStatus.BLOCKED : ProductionReadinessStatus.NOT_CONFIGURED;
    }

    private ProductionReadinessStatus dkimStatus() {
        String key = properties.getDomains().getDkimKeyEncryptionKey();
        if (key == null || key.isBlank()) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        return ProductionReadinessStatus.CONFIGURED;
    }

    private ProductionReadinessStatus rateLimiterStatus() {
        if (redis == null) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        try (RedisConnection connection = redis.getConnection()) {
            String pong = connection.ping();
            if (pong != null && "PONG".equalsIgnoreCase(pong.trim())) {
                return ProductionReadinessStatus.VERIFIED;
            }
            return ProductionReadinessStatus.BLOCKED;
        } catch (RuntimeException exception) {
            return ProductionReadinessStatus.BLOCKED;
        }
    }

    private ProductionReadinessStatus productionConfigStatus(
            boolean production,
            String hostname,
            String outboundIp,
            String bounceDomain,
            ProductionReadinessStatus tls
    ) {
        if (!production) {
            return ProductionReadinessStatus.NOT_CONFIGURED;
        }
        boolean identity = ProductionIdentity.isProductionHostname(hostname)
                && ProductionIdentity.isPublicIpv4(outboundIp)
                && ProductionIdentity.isProductionBounceDomain(bounceDomain)
                && tls == ProductionReadinessStatus.CONFIGURED;
        return identity ? ProductionReadinessStatus.CONFIGURED : ProductionReadinessStatus.BLOCKED;
    }

    private ProductionReadinessStatus spfStatus(String outboundIp) {
        if (properties.getDomains().isSpfAuthorizeOutboundIp()) {
            return ProductionIdentity.isPublicIpv4(outboundIp)
                    ? ProductionReadinessStatus.CONFIGURED
                    : ProductionReadinessStatus.BLOCKED;
        }
        if (properties.getDomains().isSpfIncludeProvisioned()) {
            return ProductionReadinessStatus.CONFIGURED;
        }
        return ProductionReadinessStatus.NOT_CONFIGURED;
    }

    private DnsSnapshot dnsSnapshot(String hostname, String outboundIp, String bounceMx) {
        synchronized (dnsLock) {
            if (cachedDns != null
                    && cachedDns.expires.isAfter(Instant.now())
                    && Objects.equals(cachedDns.hostname, hostname)
                    && Objects.equals(cachedDns.outboundIp, outboundIp)
                    && Objects.equals(cachedDns.bounceMx, bounceMx)) {
                return cachedDns.snapshot;
            }
            ProductionDnsProbe.ForwardLookup forward = ProductionDnsProbe.ForwardLookup.failure();
            ProductionDnsProbe.PtrLookup ptr = ProductionDnsProbe.PtrLookup.failure();
            ProductionDnsProbe.MxLookup mx = ProductionDnsProbe.MxLookup.failure();
            if (dnsProbe != null) {
                if (!hostname.isEmpty()) {
                    forward = dnsProbe.lookupForward(hostname);
                }
                if (ProductionIdentity.isPublicIpv4(outboundIp)) {
                    ptr = dnsProbe.lookupPtr(outboundIp);
                }
                if (!bounceMx.isEmpty() && ProductionIdentity.isProductionHostname(bounceMx)) {
                    mx = dnsProbe.lookupMx(bounceMx);
                }
            }
            DnsSnapshot snapshot = new DnsSnapshot(forward, ptr, mx);
            cachedDns = new CachedDns(Instant.now().plus(DNS_CACHE_TTL), hostname, outboundIp, bounceMx, snapshot);
            return snapshot;
        }
    }

    private record DnsSnapshot(
            ProductionDnsProbe.ForwardLookup forward,
            ProductionDnsProbe.PtrLookup ptr,
            ProductionDnsProbe.MxLookup mx
    ) {
    }

    private record CachedDns(
            Instant expires,
            String hostname,
            String outboundIp,
            String bounceMx,
            DnsSnapshot snapshot
    ) {
    }
}
