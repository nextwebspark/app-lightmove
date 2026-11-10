package app.lightmove.api.workspace.model;

/** What a client record shows of a contact's outstanding invitation: its link lapsed, or its email bounced. */
public record ClientInvitationState(boolean expired, boolean emailFailed) {}
