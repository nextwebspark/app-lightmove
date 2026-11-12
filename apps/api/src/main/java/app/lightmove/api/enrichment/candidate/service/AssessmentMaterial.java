package app.lightmove.api.enrichment.candidate.service;

/**
 * Everything the team has filed on a person, written out for the assessment in its order of weight — the position's
 * recorded calls, then the notes of calls and meetings, then the CV, then every other note and document — each group
 * held to its own share of the prompt so a long CV never crowds out what the executive said. Emails and phone numbers
 * are already taken out.
 */
record AssessmentMaterial(String transcripts, String conversationNotes, String cvs, String otherNotes,
                          String otherDocuments, int transcriptCount, int conversationNoteCount, int cvCount,
                          int otherNoteCount, int otherDocumentCount) {

    static final AssessmentMaterial NONE = new AssessmentMaterial(MaterialText.NONE_ON_FILE,
            MaterialText.NONE_ON_FILE, MaterialText.NONE_ON_FILE, MaterialText.NONE_ON_FILE,
            MaterialText.NONE_ON_FILE, 0, 0, 0, 0, 0);

    int noteCount() {
        return conversationNoteCount + otherNoteCount;
    }

    int documentCount() {
        return cvCount + otherDocumentCount;
    }

    AssessmentMaterial withoutTranscripts() {
        return new AssessmentMaterial(MaterialText.NONE_ON_FILE, conversationNotes, cvs, otherNotes, otherDocuments,
                0, conversationNoteCount, cvCount, otherNoteCount, otherDocumentCount);
    }
}
