package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Processing of a person was restricted; heard after commit by whatever still holds work on them (a Notetaker bot). */
@NamedInterface(name = "api", propagate = false)
public record PersonProcessingRestricted(UUID workspaceId, UUID personId, UUID restrictedBy) {}
