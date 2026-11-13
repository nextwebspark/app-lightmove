package app.lightmove.api.enrichment.candidate.service;

/**
 * Everything the team has filed on a person, written out for the assessment in its order of weight — the position's
 * recorded calls, then the notes of calls and meetings, then the CV, then every other note and document — each group
 * held to its own share of the prompt so a long CV never crowds out what the executive said. Emails and phone numbers
 * are already taken out.
 */
record AssessmentMaterial(MaterialGroup transcripts, MaterialGroup conversationNotes, MaterialGroup cvs,
                          MaterialGroup otherNotes, MaterialGroup otherDocuments) {

    static final AssessmentMaterial NONE = new AssessmentMaterial(MaterialGroup.NONE, MaterialGroup.NONE,
            MaterialGroup.NONE, MaterialGroup.NONE, MaterialGroup.NONE);

    int noteCount() {
        return conversationNotes.count() + otherNotes.count();
    }

    int documentCount() {
        return cvs.count() + otherDocuments.count();
    }

    AssessmentMaterial withoutTranscripts() {
        return new AssessmentMaterial(MaterialGroup.NONE, conversationNotes, cvs, otherNotes, otherDocuments);
    }
}
