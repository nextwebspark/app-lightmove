package app.lightmove.api.core.security.dto;

/**
 * What the invitee is told about their outstanding invitation. Deliberately token-free: the token
 * proved control of the invited mailbox, and a verified matching address proves the same thing —
 * the token-less accept applies identical guards.
 */
public record PendingInvitationSummary(
        String workspaceName,
        String role
) {}
