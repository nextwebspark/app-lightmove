package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;

/** Consent requests committed as sent (#1267), for the mailer once the transaction is in; a renewal has no sender. */
public record ConsentRequestsQueued(UUID workspaceId, UUID senderUserId, List<QueuedConsentRequest> requests) {}
