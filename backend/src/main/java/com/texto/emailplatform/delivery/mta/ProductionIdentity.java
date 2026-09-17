package com.texto.emailplatform.delivery.mta;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production SMTP identity contracts. These checks are syntactic / RFC-range only.
 * Passing them does not mean public DNS, PTR, or Internet delivery exist.
 */
public final class ProductionIdentity {

    private static final Pattern IPV4 = Pattern.compile(
            "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$"
    );
    private static final Pattern FQDN = Pattern.compile(
            "^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    );

    private ProductionIdentity() {
    }

    public static boolean isProductionHostname(String hostname) {
        String value = normalize(hostname);
        if (value.isEmpty() || !FQDN.matcher(value).matches()) {
            return false;
        }
        if (value.equals("localhost") || value.equals("texto.local")) {
            return false;
        }
        return !isReservedSuffix(value);
    }

    public static boolean isProductionBounceDomain(String domain) {
        return isProductionHostname(domain);
    }

    public static boolean isPublicIpv4(String ip) {
        int address = parseIpv4(ip);
        return address >= 0 && !isNonPublicIpv4(address);
    }

    /**
     * @return IPv4 as an int, or {@code -1} if the value is not a dotted-quad IPv4 literal
     */
    static int parseIpv4(String ip) {
        if (ip == null || ip.isBlank()) {
            return -1;
        }
        String trimmed = ip.trim();
        Matcher matcher = IPV4.matcher(trimmed);
        if (!matcher.matches()) {
            return -1;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(matcher.group(i + 1));
            if (octet > 255) {
                return -1;
            }
            octets[i] = octet;
        }
        try {
            InetAddress parsed = InetAddress.getByName(trimmed);
            if (!(parsed instanceof Inet4Address) || !trimmed.equals(parsed.getHostAddress())) {
                return -1;
            }
        } catch (UnknownHostException exception) {
            return -1;
        }
        return (octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3];
    }

    static boolean isNonPublicIpv4(int address) {
        int unsigned = address;
        if (inCidr(unsigned, 0x00000000, 8)) {
            return true;
        }
        if (inCidr(unsigned, 0x7F000000, 8)) {
            return true;
        }
        if (inCidr(unsigned, 0x0A000000, 8)) {
            return true;
        }
        if (inCidr(unsigned, 0xAC100000, 12)) {
            return true;
        }
        if (inCidr(unsigned, 0xC0A80000, 16)) {
            return true;
        }
        if (inCidr(unsigned, 0xA9FE0000, 16)) {
            return true;
        }
        if (inCidr(unsigned, 0xE0000000, 4)) {
            return true;
        }
        if (inCidr(unsigned, 0xF0000000, 4)) {
            return true;
        }
        if (inCidr(unsigned, 0x64400000, 10)) {
            return true;
        }
        if (inCidr(unsigned, 0xC0000000, 24)) {
            return true;
        }
        if (inCidr(unsigned, 0xC0000200, 24)) {
            return true;
        }
        if (inCidr(unsigned, 0xC6336400, 24)) {
            return true;
        }
        if (inCidr(unsigned, 0xCB007100, 24)) {
            return true;
        }
        if (inCidr(unsigned, 0xC6120000, 15)) {
            return true;
        }
        return unsigned == 0xFFFFFFFF;
    }

    private static boolean inCidr(int address, int network, int prefix) {
        int mask = prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
        return (address & mask) == (network & mask);
    }

    private static boolean isReservedSuffix(String hostname) {
        return hostname.endsWith(".test")
                || hostname.endsWith(".localhost")
                || hostname.endsWith(".local")
                || hostname.endsWith(".invalid")
                || hostname.endsWith(".example");
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
