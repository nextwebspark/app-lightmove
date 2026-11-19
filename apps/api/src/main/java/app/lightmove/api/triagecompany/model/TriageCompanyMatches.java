package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;
import org.springframework.modulith.NamedInterface;

/** The first rows of a narrowed read and how many matched in all, so a caller past the cut can tell. */
@NamedInterface(name = "api", propagate = false)
public record TriageCompanyMatches(List<TriageCompanyResponse> companies, long totalCount) {

    public boolean truncated() {
        return totalCount > companies.size();
    }
}
