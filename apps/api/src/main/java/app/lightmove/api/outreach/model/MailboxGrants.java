package app.lightmove.api.outreach.model;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Our own grant ids, {@code direct:<provider>:<uuid>}. Nylas's are bare UUIDs, so the prefix alone says which
 * gateway holds a grant, and the provider in it says which of ours — a revoke after the row is gone included.
 */
public final class MailboxGrants {

    private static final String DIRECT_PREFIX = "direct:";

    private MailboxGrants() {}

    public static String mintDirect(String provider) {
        return DIRECT_PREFIX + provider.toLowerCase(Locale.ROOT) + ":" + UUID.randomUUID();
    }

    public static boolean isDirect(String grantId) {
        return grantId != null && grantId.startsWith(DIRECT_PREFIX);
    }

    /** The provider a direct grant was made at, in the gateways' own names ({@code google}, {@code microsoft}). */
    public static Optional<String> directProviderOf(String grantId) {
        if (!isDirect(grantId)) {
            return Optional.empty();
        }
        String rest = grantId.substring(DIRECT_PREFIX.length());
        int colon = rest.indexOf(':');
        return colon <= 0 ? Optional.empty() : Optional.of(rest.substring(0, colon));
    }
}
