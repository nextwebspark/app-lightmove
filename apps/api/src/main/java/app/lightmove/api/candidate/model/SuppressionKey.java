package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.SuppressionKind;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.security.token.Tokens;
import java.util.Locale;
import java.util.UUID;

/**
 * A suppressible key as the list stores it: the kind and a hash of the normalised value, salted with the
 * workspace so one firm's list says nothing about another's. Normalised exactly as the matcher reads a person —
 * the profile slug {@link LinkedInUrls} takes, the email's ledger key — or a key that matched a person would
 * miss their suppression.
 */
public record SuppressionKey(SuppressionKind kind, String hash) {

    public static SuppressionKey ofProfileSlug(UUID workspaceId, String slug) {
        return new SuppressionKey(SuppressionKind.PROFILE_SLUG,
                hashOf(workspaceId, SuppressionKind.PROFILE_SLUG, slug.trim().toLowerCase(Locale.ROOT)));
    }

    /** Null for a URL that names no member profile. */
    public static SuppressionKey ofProfileUrlOrNull(UUID workspaceId, String linkedinUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        return slug == null ? null : ofProfileSlug(workspaceId, slug);
    }

    /** Null for a blank address. */
    public static SuppressionKey ofEmailOrNull(UUID workspaceId, String email) {
        if (email == null) {
            return null;
        }
        String key = CandidateContact.keyOf(ContactChannel.EMAIL, email);
        return key.isEmpty() ? null : new SuppressionKey(SuppressionKind.EMAIL,
                hashOf(workspaceId, SuppressionKind.EMAIL, key));
    }

    private static String hashOf(UUID workspaceId, SuppressionKind kind, String normalised) {
        return Tokens.hash(workspaceId + ":" + kind.name() + ":" + normalised);
    }
}
