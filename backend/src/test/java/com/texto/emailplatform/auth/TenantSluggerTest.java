package com.texto.emailplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantSluggerTest {

    @Test
    void slugifiesOrganizationNames() {
        assertThat(TenantSlugger.slugify("Get Set Go World")).isEqualTo("get-set-go-world");
        assertThat(TenantSlugger.slugify("  Texto  ")).isEqualTo("texto");
        assertThat(TenantSlugger.slugify("!!!")).isEqualTo("tenant");
    }
}
