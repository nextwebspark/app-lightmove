package app.lightmove.api.enrichment.candidate.service;

/** One group of what is on file as a prompt reads it — its text, and how many items of it were sent. */
record MaterialGroup(String text, int count) {

    static final MaterialGroup NONE = new MaterialGroup(MaterialText.NONE_ON_FILE, 0);
}
