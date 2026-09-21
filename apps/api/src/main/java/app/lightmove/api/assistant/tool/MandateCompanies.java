package app.lightmove.api.assistant.tool;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;

/**
 * The companies a mandate has filed at one stage, and how many of them are here.
 *
 * <p>{@link CompanyMatches}' reasoning again: a stage's size is the answer to "how far have we got",
 * and a page of it read as the whole understates the mandate's own progress to whoever asked.
 */
public record MandateCompanies(long matched, int showing, List<MandateCompanySummary> companies) {

    static MandateCompanies of(long matched, List<TriageCompanyResponse> page) {
        return new MandateCompanies(matched, page.size(),
                page.stream().map(MandateCompanySummary::of).toList());
    }
}
