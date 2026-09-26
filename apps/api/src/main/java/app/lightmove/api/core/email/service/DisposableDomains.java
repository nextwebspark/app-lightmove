package app.lightmove.api.core.email.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Throwaway-inbox domains, rejected at signup. A cheap blocklist that turns away the low-effort
 * majority — <b>not</b> a security control; nothing should treat a non-disposable domain as proof.
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
