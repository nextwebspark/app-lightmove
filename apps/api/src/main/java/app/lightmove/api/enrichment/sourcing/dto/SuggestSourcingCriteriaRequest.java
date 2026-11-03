package app.lightmove.api.enrichment.sourcing.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The companies the panel holds, whose countries tell the AI which languages titles are written in; empty for none. */
public record SuggestSourcingCriteriaRequest(
        @Size(max = 200, message = "That is more companies than one search may take") List<UUID> triageCompanyIds
) {}
