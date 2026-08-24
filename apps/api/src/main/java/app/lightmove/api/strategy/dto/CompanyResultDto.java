package app.lightmove.api.strategy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One company in the Strategy screen's results table.
 *
 * <p>Carries every field the table can show, not only the ones a given user has switched on — the
 * visible set is a client-side preference, and making the response shape depend on it would put UI
 * state in the query key for the sake of a few hundred bytes a row.
 *
 * <p>{@code annualRevenue} is null on roughly nine rows in ten, and the funding fields are sparser
 * still. That is the data, not a read failure, and the client renders it as unknown rather than zero.
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
) {}
