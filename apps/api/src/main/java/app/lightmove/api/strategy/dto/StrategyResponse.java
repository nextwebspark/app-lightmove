package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * Everything the Strategy screen needs before it draws: the live filter, the mandate's off-limits
 * list, and the searches saved against it.
 *
 * <p>The three arrive together because the screen cannot render usefully without all of them — the
 * sidebar needs the filter, the toolbar needs the searches, and the off-limits chip means nothing
 * without knowing whether anything is barred. The universe's own facet counts are the one thing not
 * here: they are the same for every mandate in the workspace, so they are a separate cacheable read.
 */
public record StrategyResponse(StrategyFilterDto filter, List<CompanyRefDto> offLimits,
                                List<SavedSearchResponse> searches) {}
