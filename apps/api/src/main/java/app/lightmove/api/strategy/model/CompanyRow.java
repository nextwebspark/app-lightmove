package app.lightmove.api.strategy.model;

import java.time.LocalDate;
import java.util.List;

/**
 * One row of the Apollo company universe, as read back for a filtered list — wider than the table's
 * visible columns, because the user chooses which to show.
 *
 * <p>{@code numEmployees} and {@code annualRevenue} are the raw figures, not band labels, so a caller
 * sorting by size has the number; revenue is null on roughly nine rows in ten, which is the data
 * rather than a read failure. {@code foundedYear} is an {@code Integer} though the column is
 * {@code smallint} — the narrower type bought nothing but a cast that fails. The array fields are
 * never null: an absent {@code text[]} reads back as an empty list.
 */
public record CompanyRow(String apolloAccountId, String companyName, String industry,
                          String companyCountry, String companyCity, Integer numEmployees,
                          Long annualRevenue, String website, String logoUrl,
                          String shortDescription, Integer foundedYear,
                          String companyLinkedinUrl, String facebookUrl, String twitterUrl,
                          String companyPhone, String companyState, String companyAddress,
                          String parentCompany, Long totalFunding, String latestFunding,
                          Long latestFundingAmount, LocalDate lastRaisedAt,
                          Integer numberOfRetailLocations, List<String> keywords,
                          List<String> technologies, List<String> sicCodes,
                          List<String> naicsCodes) {}
