package app.lightmove.api.enrichment.candidate.service;

/** The records of a position's recorded calls with a person, written out for a prompt, and how many went. */
record CallRecordsText(String text, int count) {}
