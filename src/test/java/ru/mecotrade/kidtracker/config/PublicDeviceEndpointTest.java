package ru.mecotrade.kidtracker.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PublicDeviceEndpointTest {
    @Test void unsetHasNoLanOrListenerFallback() {
        PublicDeviceEndpoint endpoint = new PublicDeviceEndpoint("", 0);
        assertNull(endpoint.getPublicHost()); assertNull(endpoint.getPublicPort());
    }
    @Test void acceptsExplicitIpAndHostname() {
        assertEquals("203.0.113.8", new PublicDeviceEndpoint("203.0.113.8", 9001).getPublicHost());
        assertEquals("watch.example.org", new PublicDeviceEndpoint(" watch.example.org ", 9001).getPublicHost());
    }
    @Test void rejectsIncompleteOrUnsafeSmsDestinations() {
        assertThrows(IllegalArgumentException.class, () -> new PublicDeviceEndpoint("", 9001));
        assertThrows(IllegalArgumentException.class, () -> new PublicDeviceEndpoint("watch.example.org", 0));
        assertThrows(IllegalArgumentException.class, () -> new PublicDeviceEndpoint("watch.example.org", 65536));
        assertThrows(IllegalArgumentException.class, () -> new PublicDeviceEndpoint("https://watch.example.org", 9001));
        assertThrows(IllegalArgumentException.class, () -> new PublicDeviceEndpoint("example.org,9001#", 9001));
    }
}
