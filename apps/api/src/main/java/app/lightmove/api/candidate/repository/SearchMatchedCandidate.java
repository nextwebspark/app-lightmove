package app.lightmove.api.candidate.repository;

import java.util.UUID;

/** An executive of a mandate the search box matched on their own, with where they are listed. */
public interface SearchMatchedCandidate {

    UUID getId();

    UUID getTriageCompanyId();

    String getTriageStatus();
}
