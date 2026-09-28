package app.lightmove.api.candidate.model;

import java.time.Instant;

/** A candidate's last AI assessment, last nationality reading and last failed run — any may be null. */
public record CandidateAiEnrichState(CandidateAiAssessment assessment, NationalityReading nationalityReading,
                                     Instant failedAt) {}
