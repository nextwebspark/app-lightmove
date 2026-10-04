package app.lightmove.api.enrichment.contact.service;

import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;

/**
 * Looks up an executive's email and phone from their LinkedIn profile — two methods because the
 * channels bill from separate pools. A miss is an empty answer, never an exception; any other failure
 * is a {@code VendorException}.
 */
public interface ContactFinder {

    FoundEmails findEmails(String linkedinUrl);

    FoundPhones findPhones(String linkedinUrl);

    boolean isOffered();
}
