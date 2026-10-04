package app.lightmove.api.core.email.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consumer email providers (gmail, outlook, …), blocked at signup when {@code block-public-domains} is on.
 * Distinct from {@link DisposableDomains}: Gmail is not disposable, it is simply not a company.
 */
final class PublicEmailDomains {

    /** Overridable and extendable through {@code lightmove.email.validation.public-domains} / {@code .extra-public-domains}. */
    private static final Set<String> BUNDLED = Set.of(
            "gmail.com", "googlemail.com",
            "outlook.com", "hotmail.com", "hotmail.co.uk", "live.com", "msn.com",
            "yahoo.com", "yahoo.co.uk", "yahoo.co.in", "ymail.com", "rocketmail.com",
            "icloud.com", "me.com", "mac.com",
            "proton.me", "protonmail.com", "tutanota.com", "tuta.io", "hushmail.com",
            "aol.com", "gmx.com", "gmx.net", "mail.com", "zoho.com", "yandex.com",
            "yandex.ru", "fastmail.com", "inbox.com", "email.com",
            // Regional providers common in the GCC and South Asia, where LightMove sells
            "rediffmail.com", "qq.com", "163.com", "126.com", "naver.com", "daum.net"
    );

    private final Set<String> domains;

    /**
     * @param override replaces the bundled list outright when non-empty
     * @param extra    added to whichever list is in force
     */
    PublicEmailDomains(Collection<String> override, Collection<String> extra) {
        // Normalise BEFORE testing for emptiness: Spring binds @DefaultValue("") on a List<String> to
        // [""], not []. Checking the raw collection took the override branch, filtered the blank away
        // and left the blocklist empty — signup silently stopped rejecting Gmail.
        Set<String> normalisedOverride = normalise(override);
        Set<String> base = normalisedOverride.isEmpty() ? BUNDLED : normalisedOverride;

        this.domains = new HashSet<>(base);
        this.domains.addAll(normalise(extra));
    }

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
