package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateStatus;

/** How many of one mandate's executives stand at one status — the position page's chips. */
public interface CandidateStatusCount {

    CandidateStatus getStatus();

    long getTotal();
}
