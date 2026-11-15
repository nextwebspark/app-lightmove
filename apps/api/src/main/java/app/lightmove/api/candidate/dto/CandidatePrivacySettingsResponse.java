package app.lightmove.api.candidate.dto;

/**
 * Settings → Data & retention → Candidate privacy, and what the drawer needs to offer a notice. {@code
 * defaultLawfulBasis} is a {@code LawfulBasis} wire token and {@code privacyMode} a {@code CandidatePrivacyMode} one;
 * {@code readyForConsent} says consent requests can be sent (#1267).
 */
public record CandidatePrivacySettingsResponse(
        String controllerName,
        String privacyContactEmail,
        String noticeUrl,
        String defaultLawfulBasis,
        boolean readyForNotices,
        String privacyMode,
        boolean keepOnFile,
        int keepOnFileMonths,
        boolean shareWithClient,
        boolean marketing,
        int renewalDaysBefore,
        boolean readyForConsent
) {}
