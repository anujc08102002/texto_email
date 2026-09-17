package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real DNS lookups for readiness diagnostics. Never treats lookup failure as verification.
 */
@Component
public class JndiProductionDnsProbe implements ProductionDnsProbe {

    private static final Logger log = LoggerFactory.getLogger(JndiProductionDnsProbe.class);

    private final EmailPlatformProperties properties;

    public JndiProductionDnsProbe(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public ForwardLookup lookupForward(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return ForwardLookup.failure();
        }
        List<String> addresses = lookup(hostname.trim().toLowerCase(Locale.ROOT), "A");
        if (addresses.isEmpty()) {
            return ForwardLookup.failure();
        }
        return new ForwardLookup(true, List.copyOf(addresses));
    }

    @Override
    public PtrLookup lookupPtr(String ipv4) {
        int parsed = ProductionIdentity.parseIpv4(ipv4);
        if (parsed < 0) {
            return PtrLookup.failure();
        }
        String reversed = reversedOctets(ipv4.trim()) + ".in-addr.arpa";
        List<String> names = lookup(reversed, "PTR");
        if (names.isEmpty()) {
            return PtrLookup.failure();
        }
        return new PtrLookup(true, List.copyOf(names));
    }

    @Override
    public MxLookup lookupMx(String domain) {
        if (domain == null || domain.isBlank()) {
            return MxLookup.failure();
        }
        List<String> hosts = lookup(domain.trim().toLowerCase(Locale.ROOT), "MX");
        if (hosts.isEmpty()) {
            return MxLookup.failure();
        }
        List<String> normalized = new ArrayList<>();
        for (String host : hosts) {
            String name = mxHost(host);
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }
        if (normalized.isEmpty()) {
            return MxLookup.failure();
        }
        return new MxLookup(true, List.copyOf(normalized));
    }

    private List<String> lookup(String name, String type) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(Math.max(500, properties.getDomains().getDnsTimeoutMs())));
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(name, new String[] {type});
            Attribute attribute = attributes.get(type);
            if (attribute == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            NamingEnumeration<?> enumeration = attribute.getAll();
            try {
                while (enumeration.hasMore()) {
                    Object value = enumeration.next();
                    if (value != null) {
                        String text = value.toString().trim();
                        if (!text.isEmpty()) {
                            values.add(text.replaceAll("\\.$", "").toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } finally {
                enumeration.close();
            }
            return values;
        } catch (NamingException exception) {
            log.debug("Production DNS {} lookup did not verify", type);
            return List.of();
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                    // best-effort
                }
            }
        }
    }

    private static String reversedOctets(String ipv4) {
        String[] parts = ipv4.split("\\.");
        return parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0];
    }

    private static String mxHost(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        int space = value.indexOf(' ');
        if (space > 0) {
            return value.substring(space + 1).trim();
        }
        return value;
    }
}
