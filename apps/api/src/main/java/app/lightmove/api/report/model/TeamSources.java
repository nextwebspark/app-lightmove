package app.lightmove.api.report.model;

import java.util.Map;
import java.util.UUID;

/**
 * What the researcher breakdown adds to the report's own sources: who filed each executive
 * ({@code addedByCandidate}, candidate id → user id) and every such user, plus the staff seats, named.
 * Ordered as the table lists them — staff by name first, then former members.
 */
public record TeamSources(Map<UUID, UUID> addedByCandidate, Map<UUID, ResearcherIdentity> researchers) {}
