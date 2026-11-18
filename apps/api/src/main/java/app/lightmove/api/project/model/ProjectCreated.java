package app.lightmove.api.project.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A position was created, published in the transaction that creates it; listeners set up its parts in that one. */
@NamedInterface(name = "api", propagate = false)
public record ProjectCreated(UUID workspaceId, UUID projectId, String positionTitle, String hqCountry,
                             UUID actorUserId) {}
