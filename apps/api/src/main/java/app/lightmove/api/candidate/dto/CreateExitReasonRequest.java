package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.ExitReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** A new reason, last in the list. {@code statusIds}, out-of-process statuses only, defaults to none. */
public record CreateExitReasonRequest(
        @NotBlank @Size(max = ExitReason.MAX_LABEL) String label,
        Boolean requiresNote,
        @Size(max = 200) List<UUID> statusIds
) {}
