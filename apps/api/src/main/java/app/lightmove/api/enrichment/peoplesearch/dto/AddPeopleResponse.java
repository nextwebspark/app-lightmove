package app.lightmove.api.enrichment.peoplesearch.dto;

import java.util.List;
import java.util.UUID;

/**
 * What an add did: {@code skipped} were already mapped in the mandate, {@code unavailable} had aged out
 * of the people cache since the search — nothing is bought to add a person, so those need a new search —
 * and {@code elsewhere} of the added joined an employer the mandate already holds at another stage.
 * {@code filed} names each person now in the mandate, so the page can open their contacts at once.
 */
public record AddPeopleResponse(int added, int skipped, int unavailable, int elsewhere, List<Filed> filed) {

    public record Filed(String linkedinSlug, UUID candidateId) {}
}
