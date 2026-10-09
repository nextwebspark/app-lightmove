package app.lightmove.api.gettingstarted.dto;

import java.util.List;
import java.util.UUID;

/** Only the steps this person is offered; {@code focusProjectId} is the newest position they hold a seat on. */
public record GettingStartedResponse(boolean dismissed, UUID focusProjectId, List<GettingStartedStepResponse> steps) {}
