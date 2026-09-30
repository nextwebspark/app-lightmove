package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Locale;
import java.util.Map;

/**
 * Where the companies a caller asked about already stand in one mandate: by Apollo account id, and by
 * name for a company the mandate may hold without one. Absent means the mandate holds no row for it.
 */
public record MandateStages(Map<String, TriageCompanyStatus> byAccountId,
                            Map<String, TriageCompanyStatus> byLowerCaseName) {

    public static final MandateStages NONE = new MandateStages(Map.of(), Map.of());

    public MandateStages {
        byAccountId = Map.copyOf(byAccountId);
        byLowerCaseName = Map.copyOf(byLowerCaseName);
    }

    /** The id first, then the name — the order a capture resolves a company it already holds. */
    public TriageCompanyStatus stageOf(String apolloAccountId, String companyName) {
        if (apolloAccountId != null && byAccountId.containsKey(apolloAccountId)) {
            return byAccountId.get(apolloAccountId);
        }
        return companyName == null ? null : byLowerCaseName.get(companyName.toLowerCase(Locale.ROOT));
    }
}
