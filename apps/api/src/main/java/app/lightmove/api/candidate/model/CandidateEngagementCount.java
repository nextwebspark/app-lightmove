package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * One mandate's engagement: how many executives it has mapped, how many have been acted on at all,
 * and how many are worth putting in front of the client.
 */
public record CandidateEngagementCount(UUID projectId, long total, long pastIdentified, long qualified) {
}
