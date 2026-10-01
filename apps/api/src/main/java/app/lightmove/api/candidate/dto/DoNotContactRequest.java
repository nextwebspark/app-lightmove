package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sets or clears do not contact. The reason is kept only while it is set. */
public record DoNotContactRequest(
        @NotNull Boolean doNotContact,
        @Size(max = 500) String reason
) {}
