package app.lightmove.api.enrichment.peoplesearch.dto;

/**
 * What an add did: {@code skipped} were already mapped in the mandate, {@code unavailable} had aged out
 * of the people cache since the search — nothing is bought to add a person, so those need a new search.
 */
public record AddPeopleResponse(int added, int skipped, int unavailable) {}
