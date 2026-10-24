package app.lightmove.api.enrichment.candidate.model;

/**
 * An executive written out for a prompt the way every AI run on them reads them — the profile, then the notes the
 * position reads, the documents on file and the record of each call the position recorded with them — with emails and
 * phone numbers already taken out.
 */
public record ExecutiveReadingText(String profile, String notes, String documents, String calls, int noteCount,
                                   int documentCount, int callCount) {

    public ExecutiveReadingText withoutCalls() {
        return new ExecutiveReadingText(profile, notes, documents, "none on file", noteCount, documentCount, 0);
    }
}
