package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Where a mandate's research on someone has got to, and nothing else.
 *
 * <p>Separate from {@link SaveCandidateRequest} because the two writes are not the same act. That one
 * is the profile drawer's whole form and replaces the record; this is the status pill on the read-only
 * panel, which a researcher flicks while reading. Making the pill re-submit twenty fields would let a
 * panel opened five minutes ago overwrite an edit made since — and "I moved them to Contacted" is not
 * "I replaced their profile".
 */
public record UpdateCandidateStatusRequest(
        @NotBlank(message = "A status is required")
        @Size(max = 32)
        String status
) {}
