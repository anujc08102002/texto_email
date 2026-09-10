package com.texto.emailplatform.domain;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.domain.dns.DnsErrorClassifier;
import com.texto.emailplatform.domain.dns.DnsLookupOutcome;
import com.texto.emailplatform.domain.dns.DnsTxtQueryResult;
import com.texto.emailplatform.domain.dns.DnsTxtRecordParser;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JndiDnsLookupService implements DnsLookupService {

    private static final Logger log = LoggerFactory.getLogger(JndiDnsLookupService.class);

    private final EmailPlatformProperties properties;

    public JndiDnsLookupService(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public DnsTxtQueryResult lookupTxt(String name) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(Math.max(500, properties.getDomains().getDnsTimeoutMs())));
        env.put("com.sun.jndi.dns.timeout.retries", "2");

        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(name, new String[] {"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) {
                return DnsTxtQueryResult.missing();
            }
            List<String> values = new ArrayList<>();
            NamingEnumeration<?> enumeration = txt.getAll();
            try {
                while (enumeration.hasMore()) {
                    Object value = enumeration.next();
                    if (value == null) {
                        return DnsTxtQueryResult.malformed();
                    }
                    values.add(DnsTxtRecordParser.reconstruct(value));
                }
            } finally {
                enumeration.close();
            }
            if (values.isEmpty()) {
                return DnsTxtQueryResult.missing();
            }
            return DnsTxtQueryResult.ok(values);
        } catch (NamingException exception) {
            DnsLookupOutcome outcome = DnsErrorClassifier.classify(exception);
            log.debug("DNS TXT lookup failed for outcome {}", outcome);
            return switch (outcome) {
                case NXDOMAIN -> DnsTxtQueryResult.nxdomain();
                case TIMEOUT -> DnsTxtQueryResult.timeout();
                case MALFORMED -> DnsTxtQueryResult.malformed();
                default -> DnsTxtQueryResult.temporaryFailure();
            };
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                    // Resource release best-effort.
                }
            }
        }
    }
}
