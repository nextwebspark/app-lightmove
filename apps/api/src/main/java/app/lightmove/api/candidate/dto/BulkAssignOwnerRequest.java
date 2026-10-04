package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** One owner, or nobody, for every person named. */
public record BulkAssignOwnerRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> personIds,
        UUID ownerUserId
) {}
