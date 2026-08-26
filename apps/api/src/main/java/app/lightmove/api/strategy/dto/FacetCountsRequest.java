package app.lightmove.api.strategy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The selection to count against — the sidebar's draft, sent as it stands rather than read back from
 * the mandate's saved row.
 *
 * <p>The filter autosaves on a debounce, so counts resolved from the stored document would trail
 * every chip click by the better part of a second and be wrong for the whole of it. Carrying the
 * draft widens nothing: the response is aggregate counts over ETL reference data, the endpoint still
 * takes a seat on the project, and the off-limits list — the one part of the scope that is a standing
 * decision rather than a draft — is still read server-side from the stored strategy.
 */
public record FacetCountsRequest(
        @NotNull(message = "A filter is required")
        @Valid
        StrategyFilterDto filter
) {}
