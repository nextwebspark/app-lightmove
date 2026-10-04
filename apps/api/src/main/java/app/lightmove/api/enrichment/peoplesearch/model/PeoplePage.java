package app.lightmove.api.enrichment.peoplesearch.model;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import java.util.List;

/** One page of a people-first search: who came back, how many match in all, and what was paid for. */
public record PeoplePage(List<BrightDataPerson> people, long total, int billed, int cached) {

    public PeoplePage {
        people = List.copyOf(people);
    }
}
