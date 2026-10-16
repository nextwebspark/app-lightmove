package app.uncava.api.candidate.dto;

import app.uncava.api.candidate.model.CandidateTag;
import jakarta.validation.constraints.Size;

/** An admin's change to a tag; each field left null is left as it is. */
public record UpdateCandidateTagRequest(
        @Size(min = 1, max = CandidateTag.MAX_LABEL) String label,
        String colour,
        Boolean retired
) {}
