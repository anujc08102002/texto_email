package com.texto.emailplatform.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.ops.security.OpsAuthorization;
import com.texto.emailplatform.ops.security.PlatformAdminPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OpsAuthorizationTest {

    @Test
    void requireAcceptsPlatformAdminPrincipal() {
        PlatformAdminPrincipal principal = new PlatformAdminPrincipal(UUID.randomUUID(), "ops@texto.test", "Ops");
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "redacted",
                List.of(new SimpleGrantedAuthority(OpsAuthorization.ROLE_PLATFORM_ADMIN))
        );
        assertThat(OpsAuthorization.require(authentication).email()).isEqualTo("ops@texto.test");
    }

    @Test
    void requireRejectsMissingAndNonAdminPrincipals() {
        assertThatThrownBy(() -> OpsAuthorization.require(null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("UNAUTHENTICATED");

        var tenant = new UsernamePasswordAuthenticationToken("user", "redacted", List.of());
        assertThatThrownBy(() -> OpsAuthorization.require(tenant))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void parseUuidValidates() {
        UUID id = UUID.randomUUID();
        assertThat(OpsParse.uuid(id.toString(), "tenantId")).isEqualTo(id);
        assertThat(OpsParse.uuid(" ", "tenantId")).isNull();
        assertThatThrownBy(() -> OpsParse.uuid("nope", "tenantId"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }
}
