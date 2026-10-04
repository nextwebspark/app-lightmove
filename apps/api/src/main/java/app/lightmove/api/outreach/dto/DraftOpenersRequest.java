package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** One press of the review's drafting: a batch on entering Review, or one person's Redraft. */
public record DraftOpenersRequest(
        @NotEmpty
        @Size(max = 10, message = "Draft at most ten openers at a time")
        List<UUID> candidateIds) {}
