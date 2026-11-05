package app.lightmove.api.workspace.model;

import java.util.UUID;

/**
 * A client contact's invitation was withdrawn — cancelled, or its inviter left — announced in primitives so the
 * client record's contact row follows it without {@code workspace} reaching into {@code project}.
 */
public record ClientInvitationWithdrawn(UUID workspaceId, UUID clientId, String email) {}
