package app.lightmove.api.enrichment.contact.service;

import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;

/**
 * Where an executive's email and phone are looked up from their LinkedIn profile.
 *
 * <p>Two methods rather than one taking a channel, for the reason {@code Geocoder} has {@code city}
 * and {@code country}: they draw on separate credit pools and each carries its own retry policy.
 *
 * <p><b>A miss is an empty answer, never an exception.</b> The provider having nothing on record is
 * something the drawer shows and the row remembers; every other failure is a {@code VendorException}
 * for the caller to translate.
 */
public interface ContactFinder {

    FoundEmails findEmails(String linkedinUrl);

    FoundPhones findPhones(String linkedinUrl);

    /** Whether this deployment can look contacts up at all — false when no account is configured. */
    boolean isOffered();
}
