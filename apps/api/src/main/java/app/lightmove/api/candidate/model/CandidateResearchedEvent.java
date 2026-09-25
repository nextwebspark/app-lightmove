package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.BackgroundField;
import java.util.Set;
import java.util.UUID;

/**
 * Published once a captured executive's vendor research has been written, so the background inference
 * runs after that commit — the research reaches the screen without waiting on the model.
 *
 * <p>{@code missing} is what nobody has filled in yet, and nothing outside it is ever written.
 */
public record CandidateResearchedEvent(UUID candidateId, UUID projectId, UUID addedBy, String fullName,
                                       Set<BackgroundField> missing, EnrichedProfile research) {}
