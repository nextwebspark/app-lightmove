package app.lightmove.api.enrichment.sourcing.model;

import java.util.List;

/**
 * Everyone one company's search found, live profiles first, and what it cost: {@code credits} profiles bought
 * from the live index, {@code cached} read back free, {@code matched} counted live in all, {@code indexFound}
 * people the profile index answered from {@code indexSearches} billed searches, and {@code titleChecks} free
 * name-narrowed counts.
 */
public record FoundPeople(List<FoundPerson> people, int credits, int cached, long matched, int indexFound,
                          int indexSearches, int titleChecks) {

    public FoundPeople {
        people = List.copyOf(people);
    }

    public static FoundPeople none() {
        return new FoundPeople(List.of(), 0, 0, 0, 0, 0, 0);
    }

    /** The people looked at: those the live index counted and those the profile index answered. */
    public int considered() {
        return consideredOf(matched, indexFound);
    }

    public static int consideredOf(long matched, int indexFound) {
        return (int) Math.min(Integer.MAX_VALUE, matched + indexFound);
    }

    public FoundPeople withPeople(List<FoundPerson> replaced, int moreCredits, int moreCached) {
        return new FoundPeople(replaced, credits + moreCredits, cached + moreCached, matched, indexFound,
                indexSearches, titleChecks);
    }
}
