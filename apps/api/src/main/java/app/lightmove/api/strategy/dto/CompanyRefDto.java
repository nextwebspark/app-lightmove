package app.lightmove.api.strategy.dto;

/**
 * One off-limits entry. Its snapshot is resolved server-side, never taken from the client, which
 * could otherwise bar one company under another's name.
 */
public record CompanyRefDto(
        String apolloAccountId,
        String companyName,
        String industry,
        String companyCity,
        String companyCountry,
        String logoUrl
) {}
