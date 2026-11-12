package app.lightmove.api.candidate.repository;

import java.util.UUID;

/** An executive of a mandate the search box matched on their own, with the company they are mapped at. */
public interface SearchMatchedCandidate {

    UUID getId();

    UUID getTriageCompanyId();
}
