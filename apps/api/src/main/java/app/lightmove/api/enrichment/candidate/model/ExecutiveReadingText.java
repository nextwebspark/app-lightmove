package app.lightmove.api.enrichment.candidate.model;

/**
 * An executive written out for a prompt the way every AI run on them reads them — the profile, then the notes the
 * position reads and the documents on file — with emails and phone numbers already taken out.
 */
public record ExecutiveReadingText(String profile, String notes, String documents, int noteCount,
                                   int documentCount) {}
