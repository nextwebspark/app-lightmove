package app.lightmove.api.candidate.model;

import java.util.UUID;

/** Who filed one executive — the user behind {@code added_by}, which the candidate DTO does not carry. */
public interface CandidateAttribution {

    UUID getCandidateId();

    UUID getAddedBy();
}
