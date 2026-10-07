package app.lightmove.api.assistant.model;

import java.util.List;

/**
 * Where an answer's search leaves the mandate against its target universe, and the next searches that
 * would bring it closer. {@code inMandate} counts In universe and Shortlisted; declined companies are
 * out of the market. {@code options} is empty when the search already lands inside the target.
 */
public record AssistantRefinements(long inMandate, long newFromSearch, long projected, int targetMin,
                                   int targetMax, List<AssistantRefinement> options) {

    public AssistantRefinements {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
