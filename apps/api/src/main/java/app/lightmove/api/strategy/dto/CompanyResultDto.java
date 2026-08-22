package app.lightmove.api.strategy.dto;

/**
 * One company in the Strategy screen's results table.
 *
 * <p>Carries every field the table can show, not only the ones a given user has switched on — the
 * visible set is a client-side preference, and making the response shape depend on it would put UI
 * state in the query key for the sake of a few hundred bytes a row.
 *
 * <p>{@code annualRevenue} is null on roughly nine rows in ten. That is the data, not a read failure,
 * and the client renders it as unknown rather than as zero.
 *
 * <p>There is no off-limits flag. A barred company never reaches this response: the Off-limits panel
 * says such companies are "completely excluded from your active search results", and the
 * query honours that unconditionally rather than returning them with a marker.
 */
public record CompanyResultDto(
        String apolloAccountId,
        String companyName,
        String industry,
        String companyCountry,
        String companyCity,
        Integer numEmployees,
        Long annualRevenue,
        String website,
        String logoUrl,
        String shortDescription,
        Integer foundedYear
) {}
