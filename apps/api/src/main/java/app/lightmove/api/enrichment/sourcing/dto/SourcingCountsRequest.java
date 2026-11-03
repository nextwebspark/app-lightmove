package app.lightmove.api.enrichment.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The search as it stands on screen, unsaved edits included, and the companies it would take (empty for the default). */
public record SourcingCountsRequest(
        @Size(max = 200, message = "That is more companies than one search may take") List<UUID> triageCompanyIds,
        @NotNull @Valid SourcingCriteriaDto criteria
) {}
