package ru.mecotrade.kidtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.mecotrade.kidtracker.util.ValidationUtils;

/** The externally reachable device destination, independent of HTTP and TCP bind settings. */
@Component
public class PublicDeviceEndpoint {
    private final String publicHost;
    private final Integer publicPort;

    public PublicDeviceEndpoint(@Value("${kidtracker.server.public.host:}") String host,
                                @Value("${kidtracker.server.public.port:0}") int port) {
        String normalized = host == null ? "" : host.trim();
        if (normalized.isEmpty() && port == 0) {
            publicHost = null;
            publicPort = null;
        } else {
            if (normalized.isEmpty() || !ValidationUtils.isValidHost(normalized)
                    || port < 1 || port > 65535) {
                throw new IllegalArgumentException("Configure kidtracker.server.public.host and public.port together as host and TCP port (1-65535)");
            }
            publicHost = normalized;
            publicPort = port;
        }
    }

    public String getPublicHost() { return publicHost; }
    public Integer getPublicPort() { return publicPort; }
}
