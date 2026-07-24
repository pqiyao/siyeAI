package com.example.sillyspringboot.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ClientIpResolver {

    private static final int MAX_FORWARDED_HEADER_LENGTH = 2048;
    private static final int MAX_FORWARDED_HOPS = 32;

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(ApiRateLimitProperties properties) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        if (properties != null && properties.getTrustedProxyCidrs() != null) {
            for (String configuredCidr : properties.getTrustedProxyCidrs()) {
                String cidr = trimToEmpty(configuredCidr);
                if (cidr.isEmpty()) {
                    continue;
                }
                try {
                    matchers.add(new IpAddressMatcher(cidr));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("invalid trusted proxy CIDR: " + cidr, ex);
                }
            }
        }
        this.trustedProxies = List.copyOf(matchers);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remoteAddress = normalizeIpLiteral(request.getRemoteAddr());
        if (remoteAddress.isEmpty() || !isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = trimToEmpty(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isEmpty() && forwardedFor.length() <= MAX_FORWARDED_HEADER_LENGTH) {
            String[] hops = forwardedFor.split(",", -1);
            int inspected = 0;
            for (int index = hops.length - 1; index >= 0 && inspected < MAX_FORWARDED_HOPS; index--, inspected++) {
                String candidate = normalizeIpLiteral(hops[index]);
                if (!candidate.isEmpty() && !isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }

        String realIp = normalizeIpLiteral(request.getHeader("X-Real-IP"));
        return realIp.isEmpty() ? remoteAddress : realIp;
    }

    boolean isTrustedProxy(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeIpLiteral(String rawAddress) {
        String candidate = trimToEmpty(rawAddress);
        if (candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate)) {
            return "";
        }
        if (candidate.startsWith("[") && candidate.endsWith("]") && candidate.length() > 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        try {
            if (isIpv4Literal(candidate)) {
                InetAddress parsed = InetAddress.getByName(candidate);
                return parsed instanceof Inet4Address ? parsed.getHostAddress() : "";
            }
            if (candidate.indexOf(':') >= 0 && isIpv6LiteralCharacters(candidate)) {
                InetAddress parsed = InetAddress.getByName(candidate);
                return parsed instanceof Inet6Address ? parsed.getHostAddress() : "";
            }
        } catch (UnknownHostException ignored) {
            return "";
        }
        return "";
    }

    private static boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            int octet = 0;
            for (int index = 0; index < part.length(); index++) {
                char ch = part.charAt(index);
                if (ch < '0' || ch > '9') {
                    return false;
                }
                octet = octet * 10 + (ch - '0');
            }
            if (octet > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6LiteralCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            boolean allowed = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F')
                    || ch == ':'
                    || ch == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
