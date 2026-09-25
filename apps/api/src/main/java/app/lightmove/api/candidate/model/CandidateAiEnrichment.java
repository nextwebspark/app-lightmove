package app.lightmove.api.candidate.model;

/** What one AI enrichment run produced: background for the empty fields, and the assessment. */
public record CandidateAiEnrichment(InferredBackground background, CandidateAiAssessment assessment) {}
