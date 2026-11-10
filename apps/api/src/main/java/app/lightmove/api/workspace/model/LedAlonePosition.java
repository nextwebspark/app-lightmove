package app.lightmove.api.workspace.model;

import java.util.UUID;

/** A live position whose only lead is the member about to go, so it must be handed over first. */
public record LedAlonePosition(UUID projectId, String title) {}
