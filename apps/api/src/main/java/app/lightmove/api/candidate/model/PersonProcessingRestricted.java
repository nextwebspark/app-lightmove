package app.lightmove.api.candidate.model;

import java.util.UUID;

/** Processing of a person was restricted; heard after commit by whatever still holds work on them (a Notetaker bot). */
public record PersonProcessingRestricted(UUID workspaceId, UUID personId, UUID restrictedBy) {}
