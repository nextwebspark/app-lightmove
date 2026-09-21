package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.dto.CandidateResponse;
import java.util.List;

/**
 * The executives a mandate has mapped, and how many of them are here.
 *
 * <p>{@link CompanyMatches}' reasoning, on the people half. "How many executives have we mapped?" is
 * a number a consultant quotes to a client, and a capped list with nothing beside it answers
 * twenty-five for a mandate holding sixty. The total is already counted by the read this wraps.
 */
public record MandateExecutives(long matched, int showing, List<MandateExecutiveSummary> executives) {

    static MandateExecutives of(long matched, List<CandidateResponse> page) {
        return new MandateExecutives(matched, page.size(),
                page.stream().map(MandateExecutiveSummary::of).toList());
    }
}
