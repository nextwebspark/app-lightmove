package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import java.util.List;

/**
 * What one cached search answered: the people, how many of them were bought, how many were read back
 * free, and whether the vendor was asked at all — an index search is billed per search, not per person.
 */
record PeopleFound(List<BrightDataPerson> people, int bought, int cached, boolean askedVendor) {

    PeopleFound {
        people = List.copyOf(people);
    }
}
