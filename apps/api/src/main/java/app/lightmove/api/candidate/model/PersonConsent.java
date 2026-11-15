package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ConsentAnsweredVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Where one purpose stands for a person (V223). {@code requestExpiresAt} is set while a request waits for an answer;
 * {@code wording} is what the person last answered, the {@code request*} fields what the latest request asked.
 */
public record PersonConsent(
        UUID id,
        UUID personId,
        ConsentPurpose purpose,
        UUID projectId,
        ConsentStatus status,
        Instant requestedAt,
        UUID requestedBy,
        UUID requestLinkId,
        Instant requestExpiresAt,
        Instant answeredAt,
        ConsentAnsweredVia answeredVia,
        Instant validUntil,
        boolean clientNamed,
        String wordingVersion,
        String wording,
        String requestWordingVersion,
        String requestWording,
        boolean requestClientNamed,
        Integer requestMonths
) {

    public boolean isOpenAt(Instant now) {
        return requestExpiresAt != null && requestExpiresAt.isAfter(now);
    }

    /** Asked since {@code since}, whether or not the request is still open, or declined or withdrawn since then. */
    public boolean wasAskedOrRefusedSince(Instant since) {
        boolean refused = status == ConsentStatus.DECLINED || status == ConsentStatus.WITHDRAWN;
        return (requestedAt != null && requestedAt.isAfter(since))
                || (refused && answeredAt != null && answeredAt.isAfter(since));
    }

    /** Whether a share names the client: as an open or unanswered request asks it, otherwise as it was answered. */
    public boolean clientNamedAt(Instant now) {
        return isOpenAt(now) || answeredAt == null ? requestClientNamed : clientNamed;
    }

    /** Given and not past its date; a consent with no date lasts until withdrawn. */
    public boolean isLiveAt(Instant now) {
        return status == ConsentStatus.GIVEN && (validUntil == null || validUntil.isAfter(now));
    }
}
