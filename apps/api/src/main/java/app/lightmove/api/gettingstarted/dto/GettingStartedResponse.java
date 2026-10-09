package app.lightmove.api.gettingstarted.dto;

import java.util.List;
import java.util.UUID;

/**
 * Only the steps this person is offered: no mailbox step where none can be connected, no invite step without
 * {@code MEMBER_INVITE}. {@code focusProjectId} is the newest position, where the brief, market and executive
 * steps lead.
 */
public record GettingStartedResponse(boolean dismissed, UUID focusProjectId, List<GettingStartedStepResponse> steps) {}
