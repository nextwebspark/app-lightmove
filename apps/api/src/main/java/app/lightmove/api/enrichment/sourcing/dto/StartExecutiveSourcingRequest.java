package app.lightmove.api.enrichment.sourcing.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Which In-universe companies to search. Empty or absent means the first few with nobody mapped;
 * the real cap is {@code lightmove.enrichment.sourcing.max-companies-per-run}, this bound only
 * stops a runaway payload before the service reads the stage.
 */
public record StartExecutiveSourcingRequest(
        @Size(max = 200, message = "That is more companies than one run may take")
        List<UUID> triageCompanyIds
) {}
