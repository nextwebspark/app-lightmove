package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.SuppressionKind;
import app.lightmove.api.core.text.service.LinkedInUrls;
import java.util.Locale;
import java.util.UUID;

/**
 * A suppressible key, normalised exactly as the matcher reads a person — the profile slug {@link LinkedInUrls}
 * takes, the email's ledger key — or a key that matched a person would miss their suppression. Never stored:
 * the list keeps only a keyed hash of {@link #hashInput}.
 */
public record SuppressionKey(SuppressionKind kind, String normalised) {

    public static SuppressionKey ofProfileSlug(String slug) {
        return new SuppressionKey(SuppressionKind.PROFILE_SLUG, slug.trim().toLowerCase(Locale.ROOT));
    }

    /** Null for a URL that names no member profile. */
    public static SuppressionKey ofProfileUrlOrNull(String linkedinUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        return slug == null ? null : ofProfileSlug(slug);
    }

    /** Null for a blank address. */
    public static SuppressionKey ofEmailOrNull(String email) {
        if (email == null) {
            return null;
        }
        String key = CandidateContact.keyOf(ContactChannel.EMAIL, email);
        return key.isEmpty() ? null : new SuppressionKey(SuppressionKind.EMAIL, key);
    }

    /** Salted with the workspace, so one firm's list says nothing about another's. */
    public String hashInput(UUID workspaceId) {
        return workspaceId + ":" + kind.name() + ":" + normalised;
    }

    /** The value is a person's address or profile. */
    @Override
    public String toString() {
        return "SuppressionKey[kind=" + kind + ", normalised=<redacted>]";
    }
}
