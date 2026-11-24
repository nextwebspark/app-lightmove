package app.lightmove.api.candidate.model;

import java.util.UUID;

/** Where a person is mapped: their candidate row on one position. */
public record PersonPlacement(UUID personId, UUID projectId, UUID candidateId) {}
