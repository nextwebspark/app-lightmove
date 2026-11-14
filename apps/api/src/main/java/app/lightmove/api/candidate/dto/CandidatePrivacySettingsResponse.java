package app.lightmove.api.candidate.dto;

/**
 * Settings → Data & retention → Candidate privacy, and what the drawer needs to offer a notice. {@code
 * defaultLawfulBasis} is a {@code LawfulBasis} wire token.
 */
public record CandidatePrivacySettingsResponse(
        String controllerName,
        String privacyContactEmail,
        String noticeUrl,
        String defaultLawfulBasis,
        boolean readyForNotices
) {}
