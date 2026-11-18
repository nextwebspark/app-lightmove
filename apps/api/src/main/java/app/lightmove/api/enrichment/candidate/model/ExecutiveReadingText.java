package app.lightmove.api.enrichment.candidate.model;

import org.springframework.modulith.NamedInterface;

/**
 * An executive written out for a prompt the way every AI run on them reads them — the profile, then the notes the
 * position reads and the documents on file — with emails and phone numbers already taken out.
 */
@NamedInterface(name = "api", propagate = false)
public record ExecutiveReadingText(String profile, String notes, String documents, int noteCount,
                                   int documentCount) {}
