package app.lightmove.api.candidate.service;

/** How many addresses and numbers one person holds at a moment. */
record ContactChannelSizes(int emails, int phones) {

    static final ContactChannelSizes NONE = new ContactChannelSizes(0, 0);
}
