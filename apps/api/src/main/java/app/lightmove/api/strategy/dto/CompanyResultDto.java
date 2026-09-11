package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.CompanyRow;
import java.time.LocalDate;
import java.util.List;

/**
 * One company in the Strategy screen's results table. Carries every field the table can show, since
 * the visible set is a client-side preference.
 *
 * <p>{@code annualRevenue} is null on roughly nine rows in ten and the funding fields are sparser
 * still — the data, not a read failure. There is no off-limits flag: a barred company never reaches
 * this response at all.
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
        Integer foundedYear,
        String companyLinkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String companyPhone,
        String companyState,
        String companyAddress,
        String parentCompany,
        Long totalFunding,
        String latestFunding,
        Long latestFundingAmount,
        LocalDate lastRaisedAt,
        Integer numberOfRetailLocations,
        List<String> keywords,
        List<String> technologies,
        List<String> sicCodes,
        List<String> naicsCodes
) {

    /** Here rather than in a caller: two reads answer with this record, and the fields are positional. */
    public static CompanyResultDto of(CompanyRow row) {
        return new CompanyResultDto(
                row.apolloAccountId(),
                row.companyName(),
                row.industry(),
                row.companyCountry(),
                row.companyCity(),
                row.numEmployees(),
                row.annualRevenue(),
                row.website(),
                row.logoUrl(),
                row.shortDescription(),
                row.foundedYear(),
                row.companyLinkedinUrl(),
                row.facebookUrl(),
                row.twitterUrl(),
                row.companyPhone(),
                row.companyState(),
                row.companyAddress(),
                row.parentCompany(),
                row.totalFunding(),
                row.latestFunding(),
                row.latestFundingAmount(),
                row.lastRaisedAt(),
                row.numberOfRetailLocations(),
                row.keywords(),
                row.technologies(),
                row.sicCodes(),
                row.naicsCodes());
    }
}
