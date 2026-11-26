package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;

/**
 * The public privacy page (#923), read with no session by whoever holds the link. It names the firm and the kinds of
 * data held, never the values, since a link can be forwarded. {@code categories} are wire tokens; {@code source} a
 * {@code CandidateSource} token and {@code lawfulBasis} a {@code LawfulBasis} one. An expired link answers the firm's
 * details alone. {@code purpose} is the link's {@code PrivacyLinkPurpose} token; a consent request lists what it asks
 * in {@code consents}, a manage link the consents in force (#1267). {@code uncavaNoticeUrl} is Uncava's own notice to
 * executives (#1490), null until it is published.
 */
public record PrivacyPageResponse(
        boolean expired,
        String controllerName,
        String privacyContactEmail,
        String noticeUrl,
        String lawfulBasis,
        String source,
        Instant heldSince,
        List<String> categories,
        Instant expiresAt,
        String purpose,
        List<PrivacyPageConsentResponse> consents,
        String uncavaNoticeUrl
) {}
