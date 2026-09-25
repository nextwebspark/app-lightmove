package app.lightmove.api.candidate.model;

import java.time.Instant;

/** A candidate's last AI assessment and last failed run — either may be null. */
public record CandidateAiEnrichState(CandidateAiAssessment assessment, Instant failedAt) {}
