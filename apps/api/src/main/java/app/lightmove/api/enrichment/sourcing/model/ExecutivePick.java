package app.lightmove.api.enrichment.sourcing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * One person the model chose at a company, as the run records it: who, how well they fit and why,
 * and the row they became — null when filing them was refused as already mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutivePick(String name, int score, String reason, UUID candidateId) {}
