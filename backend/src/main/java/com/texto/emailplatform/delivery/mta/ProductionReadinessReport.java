package com.texto.emailplatform.delivery.mta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic snapshot. {@link #productionInternetReady()} is true only when every
 * required external prerequisite is actually verified — not merely configured.
 */
public record ProductionReadinessReport(
        boolean productionInternetReady,
        ProductionReadinessStatus applicationConfigured,
        ProductionReadinessStatus mtaReachable,
        ProductionReadinessStatus productionConfig,
        ProductionReadinessStatus forwardDns,
        ProductionReadinessStatus ptr,
        ProductionReadinessStatus dkimEncryption,
        ProductionReadinessStatus bounceDomain,
        ProductionReadinessStatus bounceMx,
        ProductionReadinessStatus tls,
        ProductionReadinessStatus rateLimiter,
        ProductionReadinessStatus publicDeliverySwitch,
        ProductionReadinessStatus spfAuthorization
) {

    public boolean ptrVerified() {
        return ptr == ProductionReadinessStatus.VERIFIED;
    }

    public Map<String, Object> healthDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("productionInternetReady", productionInternetReady);
        details.put("applicationConfigured", applicationConfigured.name());
        details.put("mtaReachable", mtaReachable.name());
        details.put("productionConfig", productionConfig.name());
        details.put("forwardDns", forwardDns.name());
        details.put("ptr", ptr.name());
        details.put("ptrVerified", ptrVerified());
        details.put("dkimEncryption", dkimEncryption.name());
        details.put("bounceDomain", bounceDomain.name());
        details.put("bounceMx", bounceMx.name());
        details.put("tls", tls.name());
        details.put("rateLimiter", rateLimiter.name());
        details.put("publicDeliverySwitch", publicDeliverySwitch.name());
        details.put("spfAuthorization", spfAuthorization.name());
        return details;
    }
}
