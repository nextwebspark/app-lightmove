package app.lightmove.api.enrichment.contact.service;

import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;

/**
 * What a deployment with no contact provider wires. It exists so the bean graph is total and nothing
 * downstream has to resolve an absent one; {@link #isOffered()} is what keeps the buttons hidden.
 */
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
