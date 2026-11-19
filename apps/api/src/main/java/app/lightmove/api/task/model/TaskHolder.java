package app.lightmove.api.task.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Someone holding open tasks in a workspace. */
@NamedInterface(name = "api", propagate = false)
public record TaskHolder(UUID workspaceId, UUID userId) {}
