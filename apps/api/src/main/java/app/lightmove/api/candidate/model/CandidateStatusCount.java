package app.lightmove.api.candidate.model;

import java.util.UUID;

/** One row of a mandate's executives grouped by status. */
public interface CandidateStatusCount {

    UUID getStatusId();

    long getTotal();
}
