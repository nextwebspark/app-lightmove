package app.lightmove.api.common.security;

import app.lightmove.api.common.config.LightMoveProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Who the request actually came from.
 *
 * <p>This is a security boundary, not a convenience. The value it returns keys the rate limiter's
 * per-IP budget and is written to the audit log, so a caller who can choose it can mint themselves an
 * unlimited number of login attempts and sign someone else's address to their actions.
 *
 * <p>{@code X-Forwarded-For} is a list that each proxy <i>appends</i> to. A client may send any prefix
 * it likes — the header arrives already populated with lies. Only the entries our own infrastructure
 * appended are trustworthy, and they are at the <b>right</b>-hand end. So with {@code n} trusted
 * proxies in front of us, the last {@code n} entries were written by them, and the client's real
 * address is the {@code n}-th from the right.
 *
 * <p>Taking the <b>leftmost</b> entry — the obvious reading of "the first hop is the client", and what
 * this codebase did in three separate places — hands the attacker the pen:
 *
 * <pre>
 *   X-Forwarded-For: 1.2.3.4                 (forged by the client)
 *   → our proxy appends the real peer:
 *   X-Forwarded-For: 1.2.3.4, 203.0.113.9    (203.0.113.9 is the truth)
 *   leftmost  → 1.2.3.4     ← chosen by the attacker; a fresh rate-limit bucket per request
 *   rightmost → 203.0.113.9 ← written by us
 * </pre>
 *
 * <p>With no proxy configured (the default) the header is ignored altogether and the socket's peer
 * address is used, because nothing can forge that.
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

        // Directly exposed: the header is whatever the caller felt like sending. The socket is not.
        if (trustedProxyCount == 0) {
            return orUnknown(request.getRemoteAddr());
        }

        String header = request.getHeader(FORWARDED_FOR);
        if (header == null || header.isBlank()) {
            return orUnknown(request.getRemoteAddr());
        }

        List<String> hops = List.of(header.split(","));
        // The last `trustedProxyCount` hops were appended by our own proxies; the one before them is
        // the address the outermost proxy actually saw. Fewer hops than that means the chain is shorter
        // than configured — someone is bypassing the proxy, or the count is wrong. Either way the header
        // is not evidence, so fall back to the peer.
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
