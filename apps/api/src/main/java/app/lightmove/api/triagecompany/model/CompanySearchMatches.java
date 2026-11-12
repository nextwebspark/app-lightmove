package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The mandate's companies the search box matched on their own facts, whatever their stage, each with that stage. */
public record CompanySearchMatches(Map<UUID, TriageCompanyStatus> stageById) {

    public CompanySearchMatches {
        stageById = Map.copyOf(stageById);
    }

    public Set<UUID> ids() {
        return stageById.keySet();
    }
}
