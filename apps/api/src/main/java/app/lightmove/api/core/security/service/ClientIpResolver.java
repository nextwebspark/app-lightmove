package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.LightMoveProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The request's client IP — a security boundary (per-IP rate limit, audit). Only the right-hand {@code X-Forwarded-For}
 * entries our proxies appended are trusted; the leftmost is attacker-chosen. No proxy configured: the socket peer.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private final int trustedProxyCount;

    public ClientIpResolver(LightMoveProperties properties) {
        this.trustedProxyCount = Math.max(0, properties.web().trustedProxyCount());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        if (trustedProxyCount == 0) {
            return orUnknown(request.getRemoteAddr());
        }

        String header = request.getHeader(FORWARDED_FOR);
        if (header == null || header.isBlank()) {
            return orUnknown(request.getRemoteAddr());
        }

        List<String> hops = List.of(header.split(","));
        // The hop before our proxies' is what the outermost one saw. Fewer hops means the proxy was
        // bypassed or the count is wrong: the header is not evidence, so fall back to the peer.
        int clientIndex = hops.size() - trustedProxyCount - 1;
        if (clientIndex < 0) {
            return orUnknown(request.getRemoteAddr());
        }

        return orUnknown(hops.get(clientIndex).trim());
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
