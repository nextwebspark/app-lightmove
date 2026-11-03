package app.lightmove.api.enrichment.sourcing.model;

import java.util.List;

/**
 * Everyone one company's search found that the mandate does not hold, live profiles first; how many it
 * found that the mandate already holds ({@code held}); and what it cost: {@code credits} profiles bought
 * from the live index, {@code cached} read back free, {@code matched} counted live in all,
 * {@code indexSearches} billed profile-index searches and {@code titleChecks} free name-narrowed counts.
 */
public record FoundPeople(List<FoundPerson> people, int held, int credits, int cached, long matched,
                          int indexSearches, int titleChecks) {

    public FoundPeople {
        people = List.copyOf(people);
    }

    public static FoundPeople none() {
        return new FoundPeople(List.of(), 0, 0, 0, 0, 0, 0);
    }
}
