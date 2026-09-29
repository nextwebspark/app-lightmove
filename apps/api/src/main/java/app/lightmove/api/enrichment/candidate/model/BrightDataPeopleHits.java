package app.lightmove.api.enrichment.candidate.model;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Search API's answer over the people dataset: the billed hits, each beside the record exactly as
 * the vendor sent it ({@code rawHits}, index for index — what the people cache keeps), and how many
 * matched in all.
 */
public record BrightDataPeopleHits(List<BrightDataPerson> hits, List<String> rawHits, Long totalHits) {

    public BrightDataPeopleHits {
        hits = hits == null ? List.of() : List.copyOf(hits);
        rawHits = rawHits == null ? List.of() : List.copyOf(rawHits);
    }

    /** Hits with no vendor payload beside them — a stand-in's, or a test's. */
    public static BrightDataPeopleHits of(List<BrightDataPerson> hits, Long totalHits) {
        return new BrightDataPeopleHits(hits, List.of(), totalHits);
    }

    public static BrightDataPeopleHits read(JsonNode answer, ObjectMapper json) {
        if (answer == null) {
            return of(List.of(), null);
        }
        List<BrightDataPerson> people = new ArrayList<>();
        List<String> raw = new ArrayList<>();
        for (JsonNode hit : answer.path("hits")) {
            people.add(json.treeToValue(hit, BrightDataPerson.class));
            raw.add(hit.toString());
        }
        JsonNode total = answer.path("total_hits");
        return new BrightDataPeopleHits(people, raw, total.isNumber() ? total.asLong() : null);
    }

    /** The vendor's own record for the {@code index}th hit, when it was kept. */
    public String rawOf(int index) {
        return index < rawHits.size() ? rawHits.get(index) : null;
    }
}
