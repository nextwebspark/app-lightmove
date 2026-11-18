package app.lightmove.api.workspace.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A live position whose only lead is the member about to go, so it must be handed over first. */
@NamedInterface(name = "api", propagate = false)
public record LedAlonePosition(UUID projectId, String title) {}
