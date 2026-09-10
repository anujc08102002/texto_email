package com.texto.emailplatform.domain;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Service;

@Service
public class JndiDnsLookupService implements DnsLookupService {

    @Override
    public List<String> lookupTxt(String name) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        try {
            DirContext context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(name, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            NamingEnumeration<?> enumeration = txt.getAll();
            while (enumeration.hasMore()) {
                Object value = enumeration.next();
                if (value != null) {
                    values.add(value.toString().replace("\"", ""));
                }
            }
            return values;
        } catch (NamingException exception) {
            return List.of();
        }
    }
}
