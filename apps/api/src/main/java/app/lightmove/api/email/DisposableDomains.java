package app.lightmove.api.email;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Throwaway-inbox domains, rejected at signup.
 *
 * <p>LightMove is sold to search firms and its signup asks for a <i>work</i> email. A ten-minute
 * mailbox is not one, and an account behind one cannot be recovered, cannot be audited, and cannot be
 * held to a contract.
 *
 * <p>A blocklist is inherently a losing race — new domains appear daily. It is worth keeping anyway
 * because it costs one hash lookup and turns away the low-effort majority. It is not a security
 * control, and nothing downstream should treat a non-disposable domain as proof of anything.
 */
final class DisposableDomains {

    /** The common ones. Extend via {@code lightmove.email.validation.extra-disposable-domains}. */
    private static final Set<String> KNOWN = Set.of(
            "mailinator.com", "guerrillamail.com", "guerrillamail.net", "10minutemail.com",
            "tempmail.com", "temp-mail.org", "throwawaymail.com", "yopmail.com",
            "trashmail.com", "getnada.com", "dispostable.com", "maildrop.cc",
            "fakeinbox.com", "sharklasers.com", "grr.la", "spam4.me",
            "mohmal.com", "emailondeck.com", "tempinbox.com", "mailnesia.com",
            "burnermail.io", "temp-mail.io", "moakt.com", "tmpmail.org"
    );

    private final Set<String> domains;

    DisposableDomains(Collection<String> extra) {
        this.domains = new HashSet<>(KNOWN);
        if (extra != null) {
            extra.stream()
                    .filter(domain -> domain != null && !domain.isBlank())
                    .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                    .forEach(this.domains::add);
        }
    }

    boolean contains(String domain) {
        return domain != null && domains.contains(domain.toLowerCase(Locale.ROOT));
    }
}
