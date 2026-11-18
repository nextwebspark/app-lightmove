package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * The search box resolved once for one stage, so a whole-stage read paired with its people scans for it once: the
 * companies matched on their own facts, and those with somebody listed at {@code stage} who matched.
 */
@NamedInterface(name = "api", propagate = false)
public record StageSearchMatches(TriageCompanyStatus stage, CompanySearchMatches companies,
                                 Set<UUID> companyIdsWithMatchingExecutives) {

    public StageSearchMatches {
        companyIdsWithMatchingExecutives = Set.copyOf(companyIdsWithMatchingExecutives);
    }
}
