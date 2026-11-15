package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ConsentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A consent row as it stood before a request reopened it, so a request the mail provider refuses puts it back.
 * {@code created} means the request made the row, and undoing it deletes it.
 */
public record ConsentPriorState(UUID consentId, boolean created, ConsentStatus status, Instant requestedAt,
                                UUID requestedBy, UUID requestLinkId, Instant requestExpiresAt) {}
