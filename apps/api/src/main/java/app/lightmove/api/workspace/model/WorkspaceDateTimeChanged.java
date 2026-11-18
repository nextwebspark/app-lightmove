package app.lightmove.api.workspace.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The workspace's settled zone moved; whatever copies a member's clock reads it again in the same transaction. */
@NamedInterface(name = "api", propagate = false)
public record WorkspaceDateTimeChanged(UUID workspaceId) {}
