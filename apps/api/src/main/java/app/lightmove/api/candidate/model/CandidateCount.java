package app.lightmove.api.candidate.model;

import java.util.UUID;

/** One row of a grouped count: how many executives one mandate has mapped. */
public record CandidateCount(UUID projectId, long total) {}
