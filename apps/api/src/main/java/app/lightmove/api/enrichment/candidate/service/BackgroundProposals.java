package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.candidate.model.NationalityReading;

/** What one background run answered; either half is null when it was not asked or answered nothing usable. */
record BackgroundProposals(InferredBackground background, NationalityReading reading) {

    boolean holdsNothing() {
        return (background == null || InferredBackground.NONE.equals(background)) && reading == null;
    }
}
