package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.ExitReason;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Each field left null is left as it is; {@code statusIds} replaces the statuses that offer the reason. */
public record UpdateExitReasonRequest(
        @Size(max = ExitReason.MAX_LABEL) String label,
        Boolean requiresNote,
        @Size(max = 200) List<UUID> statusIds,
        Boolean retired
) {}
