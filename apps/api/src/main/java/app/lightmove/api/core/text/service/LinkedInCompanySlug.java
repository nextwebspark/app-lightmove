package app.lightmove.api.core.text.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The company slug inside a LinkedIn URL — {@code https://www.linkedin.com/company/al-rawabi/about/}
 * is {@code al-rawabi}.
 *
 * <p>The slug rather than the URL, because the same company is written many ways: with and without
 * {@code www}, behind a country subdomain ({@code sa.linkedin.com}), with a trailing sub-page, with a
 * trailing slash, with tracking query parameters. Comparing whole URLs would call every one of those
 * a different company. The sibling of {@link WebsiteDomain}, and parsed the same way for the same
 * reason: a value that does not parse is dropped rather than compared raw.
 *
 * <p>A URL that is not a LinkedIn <i>company</i> URL yields null — a personal profile is not a company
 * however company-shaped the request that carried it.
 */
public final class LinkedInCompanySlug {

    private static final String LINKEDIN_HOST = "linkedin.com";
    private static final String COMPANY_PATH_PREFIX = "/company/";

    private LinkedInCompanySlug() {
    }

    /**
     * {@code linkedin.com} itself or a subdomain of it, and nothing else.
     *
     * <p>A plain {@code endsWith} is the trap: {@code notlinkedin.com} ends with {@code linkedin.com},
     * so any host registered under that pattern would have its slug matched against the universe as if
     * it were LinkedIn's.
     */
    private static boolean isLinkedInHost(String host) {
        if (host == null) {
            return false;
        }
        String lowered = host.toLowerCase(Locale.ROOT);
        return lowered.equals(LINKEDIN_HOST) || lowered.endsWith("." + LINKEDIN_HOST);
    }

    public static String of(String linkedInUrl) {
        if (linkedInUrl == null || linkedInUrl.isBlank()) {
            return null;
        }
        String trimmed = linkedInUrl.trim();
        String absolute = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        try {
            URI parsed = new URI(absolute);
            if (!isLinkedInHost(parsed.getHost())) {
                return null;
            }
            String path = parsed.getPath();
            if (path == null) {
                return null;
            }
            int companyAt = path.toLowerCase(Locale.ROOT).indexOf(COMPANY_PATH_PREFIX);
            if (companyAt < 0) {
                return null;
            }
            String afterPrefix = path.substring(companyAt + COMPANY_PATH_PREFIX.length());
            // Everything past the slug is a sub-page — /about, /people — and not part of the identity.
            int nextSegment = afterPrefix.indexOf('/');
            String slug = nextSegment < 0 ? afterPrefix : afterPrefix.substring(0, nextSegment);
            return slug.isBlank() ? null : slug.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }
}
