package app.lightmove.api.email;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consumer email providers — gmail, outlook, yahoo and friends.
 *
 * <p>Two jobs, and they are separate:
 *
 * <ul>
 *   <li><b>Blocking signup</b> (when {@code block-public-domains} is on). LightMove is sold to search
 *       firms, so the signup form asks for a work address and this is what enforces it.
 *   <li><b>Suppressing the colleague list</b> — always, regardless of the setting above. At signup we
 *       offer to show the workspaces already on your email domain, which is how you find your firm.
 *       {@code gmail.com} is not a firm: that list would be every Gmail user on the platform, and
 *       showing it would leak the existence of unrelated customers' workspaces to a stranger. So even
 *       with blocking switched off, a consumer domain never gets a colleague list.
 * </ul>
 *
 * <p>Distinct from {@link DisposableDomains}, which blocks throwaway inboxes. Gmail is not disposable;
 * it is simply not a company.
 */
final class PublicEmailDomains {

    /**
     * The mainstream consumer providers plus common country variants. Overridable and extendable
     * through {@code lightmove.email.validation.public-domains} / {@code .extra-public-domains}.
     */
    private static final Set<String> BUNDLED = Set.of(
            // Google
            "gmail.com", "googlemail.com",
            // Microsoft
            "outlook.com", "hotmail.com", "hotmail.co.uk", "live.com", "msn.com",
            // Yahoo
            "yahoo.com", "yahoo.co.uk", "yahoo.co.in", "ymail.com", "rocketmail.com",
            // Apple
            "icloud.com", "me.com", "mac.com",
            // Privacy-focused
            "proton.me", "protonmail.com", "tutanota.com", "tuta.io", "hushmail.com",
            // Other large consumer providers
            "aol.com", "gmx.com", "gmx.net", "mail.com", "zoho.com", "yandex.com",
            "yandex.ru", "fastmail.com", "inbox.com", "email.com",
            // Regional providers common in the GCC and South Asia, where LightMove sells
            "rediffmail.com", "qq.com", "163.com", "126.com", "naver.com", "daum.net"
    );

    private final Set<String> domains;

    /**
     * @param override replaces the bundled list outright when non-empty. For the rare deployment that
     *                 wants full control rather than "the defaults, plus a few".
     * @param extra    added to whichever list is in force. The usual way to extend it.
     */
    PublicEmailDomains(Collection<String> override, Collection<String> extra) {
        // Normalise BEFORE testing for emptiness, and the order is load-bearing. Spring binds
        // @DefaultValue("") on a List<String> to a list holding one empty string — not an empty list.
        // Checking the raw collection would see size 1, take the "caller supplied an override" branch,
        // then filter the blank away and leave the blocklist empty. Consumer domains would sail through
        // and nothing would look wrong: signup would simply stop rejecting Gmail, silently.
        Set<String> normalisedOverride = normalise(override);
        Set<String> base = normalisedOverride.isEmpty() ? BUNDLED : normalisedOverride;

        this.domains = new HashSet<>(base);
        this.domains.addAll(normalise(extra));
    }

    /** The bundled list, for a deployment that has disabled blocking but still needs the check. */
    static PublicEmailDomains bundled() {
        return new PublicEmailDomains(List.of(), List.of());
    }

    boolean contains(String domain) {
        return domain != null && domains.contains(domain.toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalise(Collection<String> input) {
        if (input == null) {
            return Set.of();
        }
        return input.stream()
                .filter(domain -> domain != null && !domain.isBlank())
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }
}
