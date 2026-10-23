package app.lightmove.api.enrichment.candidate.service;

/**
 * A person's notes, documents and, for the assessment alone, the transcripts of the position's recorded calls, written
 * out for a prompt with emails and phone numbers already taken out.
 */
record MaterialText(String notes, String documents, String transcripts, int noteCount, int documentCount,
                    int transcriptCount) {

    static final String NONE_ON_FILE = "none on file";

    static final MaterialText NONE = new MaterialText(NONE_ON_FILE, NONE_ON_FILE, 0, 0);

    MaterialText(String notes, String documents, int noteCount, int documentCount) {
        this(notes, documents, NONE_ON_FILE, noteCount, documentCount, 0);
    }

    MaterialText withTranscripts(String text, int count) {
        return new MaterialText(notes, documents, text, noteCount, documentCount, count);
    }
}
