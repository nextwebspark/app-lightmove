package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;

/** Privacy notices committed as sent (#923), for the mailer to deliver once the transaction is in. */
public record PrivacyNoticesQueued(UUID workspaceId, UUID senderUserId, List<QueuedPrivacyNotice> notices) {}
