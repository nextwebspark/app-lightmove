package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ConsentAnsweredVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentStatus;
import java.time.Instant;
import java.util.UUID;

/** Where one purpose stands for a person (V223). {@code requestExpiresAt} is set while a request waits for an answer. */
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
        String wording
) {

    public boolean isOpenAt(Instant now) {
        return requestExpiresAt != null && requestExpiresAt.isAfter(now);
    }

    /** Given and not past its date; a consent with no date lasts until withdrawn. */
    public boolean isLiveAt(Instant now) {
        return status == ConsentStatus.GIVEN && (validUntil == null || validUntil.isAfter(now));
    }
}
