package com.texto.emailplatform.delivery.mta;

import java.util.List;

/**
 * Diagnostic DNS lookups for production readiness. Failures must never be reported as verified.
 */
public interface ProductionDnsProbe {

    ForwardLookup lookupForward(String hostname);

    PtrLookup lookupPtr(String ipv4);

    MxLookup lookupMx(String domain);

    record ForwardLookup(boolean resolved, List<String> ipv4Addresses) {
        static ForwardLookup failure() {
            return new ForwardLookup(false, List.of());
        }
    }

    record PtrLookup(boolean resolved, List<String> names) {
        static PtrLookup failure() {
            return new PtrLookup(false, List.of());
        }
    }

    record MxLookup(boolean resolved, List<String> hosts) {
        static MxLookup failure() {
            return new MxLookup(false, List.of());
        }
    }
}
