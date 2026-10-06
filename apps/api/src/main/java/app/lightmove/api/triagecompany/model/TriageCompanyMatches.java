package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;

/** The first rows of a narrowed read and how many matched in all, so a caller past the cut can tell. */
public record TriageCompanyMatches(List<TriageCompanyResponse> companies, long totalCount) {

    public boolean truncated() {
        return totalCount > companies.size();
    }
}
