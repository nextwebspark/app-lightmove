package app.lightmove.api.report.model;

import java.util.Map;
import java.util.UUID;

/** Who filed each executive (candidate id → user id) and every such user, staff by name first, then former. */
public record TeamSources(Map<UUID, UUID> addedByCandidate, Map<UUID, ResearcherIdentity> researchers) {}
