package app.lightmove.api.company.model;

/**
 * A company's rebuild-stable identity plus its display snapshot, as resolved from the universe for
 * a strategy list write. Internal to the service seam — never an HTTP payload.
 */
public record CompanyRefRow(
        String source,
        String sourceId,
        String name,
        String domain,
        String slogan,
        String logo,
        String hqCity,
        String hqCountry
) {}
