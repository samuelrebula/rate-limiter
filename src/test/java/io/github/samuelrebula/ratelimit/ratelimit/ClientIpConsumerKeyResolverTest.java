package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientIpConsumerKeyResolverTest {

    @Test
    void treatsMissingAddressAsUnknown() {
        assertNull(ClientIpConsumerKeyResolver.normalize(null));
        assertNull(ClientIpConsumerKeyResolver.normalize("   "));
    }

    @Test
    void keepsAPlainIpv4Address() {
        assertEquals("203.0.113.10", ClientIpConsumerKeyResolver.normalize(" 203.0.113.10 "));
    }

    @Test
    void stripsAnIpv4Port() {
        assertEquals("203.0.113.10", ClientIpConsumerKeyResolver.normalize("203.0.113.10:54321"));
    }

    @Test
    void lowercasesIpv6AndDropsPortAndZone() {
        assertEquals("2001:db8::1", ClientIpConsumerKeyResolver.normalize("[2001:DB8::1]:443"));
        assertEquals("fe80::1", ClientIpConsumerKeyResolver.normalize("fe80::1%eth0"));
    }

    @Test
    void mapsIpv4EmbeddedInIpv6ToIpv4() {
        assertEquals("203.0.113.10", ClientIpConsumerKeyResolver.normalize("::FFFF:203.0.113.10"));
        assertEquals("::ffff:not-an-ip", ClientIpConsumerKeyResolver.normalize("::ffff:not-an-ip"));
    }
}
