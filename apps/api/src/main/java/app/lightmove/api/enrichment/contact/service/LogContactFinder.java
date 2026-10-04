package app.lightmove.api.enrichment.contact.service;

import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;

/** Wired when no contact provider is configured; {@link #isOffered()} false hides the buttons. */
public class LogContactFinder implements ContactFinder {

    @Override
    public FoundEmails findEmails(String linkedinUrl) {
        return FoundEmails.none(null);
    }

    @Override
    public FoundPhones findPhones(String linkedinUrl) {
        return FoundPhones.none(null);
    }

    @Override
    public boolean isOffered() {
        return false;
    }
}
