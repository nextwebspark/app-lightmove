package app.lightmove.api.company.dto;

/** One company matched by the name search, with what the picker needs to display and store it. */
public record CompanySearchResult(
        String source,
        String sourceId,
        String name,
        String domain,
        String slogan,
        String logo,
        String primaryIndustry,
        String hqCity,
        String hqCountry,
        Integer employeeCount
) {}
