package app.lightmove.api.workspace.model;

import org.springframework.modulith.NamedInterface;

/** What a client record shows of a contact's outstanding invitation: its link lapsed, or its email bounced. */
@NamedInterface(name = "api", propagate = false)
public record ClientInvitationState(boolean expired, boolean emailFailed) {}
