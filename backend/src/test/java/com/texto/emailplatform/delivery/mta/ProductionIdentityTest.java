package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductionIdentityTest {

    @Test
    void acceptsPublicIpv4() {
        assertThat(ProductionIdentity.isPublicIpv4("1.1.1.1")).isTrue();
        assertThat(ProductionIdentity.isPublicIpv4("8.8.8.8")).isTrue();
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalMulticastAndMalformed() {
        assertThat(ProductionIdentity.isPublicIpv4("127.0.0.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("10.0.0.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("172.16.5.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("192.168.1.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("169.254.1.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("224.0.0.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("0.0.0.0")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("192.0.2.1")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("not-an-ip")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("1.2.3")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("1.2.3.4.5")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4("")).isFalse();
        assertThat(ProductionIdentity.isPublicIpv4(null)).isFalse();
    }

    @Test
    void acceptsProductionHostname() {
        assertThat(ProductionIdentity.isProductionHostname("smtp.texto.email")).isTrue();
        assertThat(ProductionIdentity.isProductionHostname("mail.example.org")).isTrue();
    }

    @Test
    void rejectsLocalAndReservedHostnames() {
        assertThat(ProductionIdentity.isProductionHostname("localhost")).isFalse();
        assertThat(ProductionIdentity.isProductionHostname("texto.local")).isFalse();
        assertThat(ProductionIdentity.isProductionHostname("smtp.texto.test")).isFalse();
        assertThat(ProductionIdentity.isProductionHostname("foo.localhost")).isFalse();
        assertThat(ProductionIdentity.isProductionHostname("smtp")).isFalse();
        assertThat(ProductionIdentity.isProductionBounceDomain("bounce.texto.test")).isFalse();
        assertThat(ProductionIdentity.isProductionBounceDomain("bounce.texto.email")).isTrue();
    }
}
