package app.lightmove.api.strategy.dto;

/**
 * One entry on the off-limits list: its identity in the universe plus the display snapshot taken when
 * it was added. The snapshot fields are resolved server-side from the universe, never taken from the
 * client — a client that could supply them could bar one company under another's name.
 */
public record CompanyRefDto(
        String apolloAccountId,
        String companyName,
        String industry,
        String companyCity,
        String companyCountry,
        String logoUrl
) {}
