package app.lightmove.api.project.dto;

/**
 * One stored list entry: the key plus the name/display snapshot taken when it was added. The
 * snapshot fields are resolved server-side from the universe, never taken from the client.
 */
public record CompanyRefDto(
        String source,
        String sourceId,
        String name,
        String domain,
        String slogan,
        String logo,
        String hqCity,
        String hqCountry
) {}
