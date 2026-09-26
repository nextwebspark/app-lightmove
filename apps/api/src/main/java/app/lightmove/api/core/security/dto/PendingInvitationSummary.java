package app.lightmove.api.core.security.dto;

import java.util.UUID;

/**
 * What the invitee is told about an outstanding invitation. Deliberately token-free: the token
 * proved control of the invited mailbox, and a verified matching address proves the same thing —
 * the token-less accept-by-id applies identical guards.
 */
public record PendingInvitationSummary(
        UUID id,
        String workspaceName,
        String role,
        String inviterName
) {}
