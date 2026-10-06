package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateStatus;

/** One row of a mandate's executives grouped by status. */
public interface CandidateStatusCount {

    CandidateStatus getStatus();

    long getTotal();
}
