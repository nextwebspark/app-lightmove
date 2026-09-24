package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * One row of a grouped count per mandate. An interface projection rather than a record so the native
 * coverage count can answer in it too: a constructor expression is JPQL-only.
 */
public interface CandidateCount {

    UUID getProjectId();

    long getTotal();
}
