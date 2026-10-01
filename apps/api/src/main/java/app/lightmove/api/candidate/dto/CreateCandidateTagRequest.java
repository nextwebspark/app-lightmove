package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CandidateTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new tag: any staff member may make one. {@code colour} defaults to neutral. */
public record CreateCandidateTagRequest(
        @NotBlank @Size(max = CandidateTag.MAX_LABEL) String label,
        String colour
) {}
