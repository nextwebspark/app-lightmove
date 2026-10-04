package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Adds people the workspace already holds to a position, as Identified. */
public record MapPeopleToPositionRequest(
        @NotNull UUID projectId,
        @NotEmpty @Size(max = 500) List<@NotNull UUID> personIds
) {}
