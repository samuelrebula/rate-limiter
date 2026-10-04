package io.github.samuelrebula.ratelimit.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * Identifies the caller by the socket address.
 *
 * <p>The address is trimmed and lowercased. A port and an IPv6 zone id are removed.
 * {@code ::ffff:203.0.113.10} becomes {@code 203.0.113.10}, so the mapped form and the
 * plain form share one bucket. A missing address uses the single key {@code unknown}.
 *
 * <p>{@code X-Forwarded-For} is ignored unless {@code rate-limit.trust-forwarded-headers}
 * is true. That switch registers Spring's {@code ForwardedHeaderFilter} before this
 * resolver runs. The proxy in front of the application has to replace that header:
 * Spring keeps the leftmost address, and a client-supplied prefix is otherwise accepted
 * as the bucket key. Leave the switch off when the application is reachable directly.
 *
 * <p>NAT and CGNAT make unrelated callers share a limit. IPv6 can create one key per
 * address. A later strategy can read an API key or a user id and keep the storage key
 * {@code rl:{policy}:{consumer}}. This stays a concrete class until that second
 * strategy exists.
 */
public final class ClientIpConsumerKeyResolver {

    static final String UNKNOWN = "unknown";

    public String resolve(HttpServletRequest request) {
        String normalized = normalize(request.getRemoteAddr());
        return normalized == null ? UNKNOWN : normalized;
    }

    static String normalize(String remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        String value = remoteAddress.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 1) {
                value = value.substring(1, end);
            }
        } else {
            int colon = value.indexOf(':');
            if (colon > 0 && value.indexOf(':', colon + 1) < 0 && value.indexOf('.') >= 0) {
                value = value.substring(0, colon);
            }
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        value = value.toLowerCase(Locale.ROOT);
        String mappedPrefix = "::ffff:";
        if (value.startsWith(mappedPrefix)) {
            String mapped = value.substring(mappedPrefix.length());
            if (isIpv4(mapped)) {
                return mapped;
            }
        }
        return value.isEmpty() ? null : value;
    }

    private static boolean isIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) {
                return false;
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (octet < 0 || octet > 255) {
                return false;
            }
        }
        return true;
    }
}
