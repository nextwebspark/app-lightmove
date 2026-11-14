package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * A person stopped outreach emails (#924), in the transaction that marked them. {@code recipientUserId} is who hears
 * of it — their owner, else whoever sent the email they used — and null when neither is known.
 */
public record PersonOptedOut(UUID workspaceId, UUID personId, UUID recipientUserId) {}
