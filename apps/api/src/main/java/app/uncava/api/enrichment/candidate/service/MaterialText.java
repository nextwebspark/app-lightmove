package app.uncava.api.enrichment.candidate.service;

/** A person's notes and documents written out for a prompt, emails and phone numbers already taken out. */
record MaterialText(String notes, String documents, int noteCount, int documentCount) {

    static final String NONE_ON_FILE = "none on file";

    static final MaterialText NONE = new MaterialText(NONE_ON_FILE, NONE_ON_FILE, 0, 0);
}
