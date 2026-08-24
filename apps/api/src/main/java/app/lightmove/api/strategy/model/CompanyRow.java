package app.lightmove.api.strategy.model;

import java.time.LocalDate;
import java.util.List;

/**
 * One row of the Apollo company universe, as read back for a filtered list.
 *
 * <p>Wider than the Strategy table's visible columns, because the table lets a user choose which
 * columns to show and a row is a few hundred bytes — cheaper to send every offerable field than to
 * make the query take a field list and the response shape depend on client UI state.
 *
 * <p>{@code numEmployees} and {@code annualRevenue} travel as the raw figures rather than as band
 * labels: the client renders the band, and a caller sorting by size needs the number. {@code
 * annualRevenue} is null on roughly nine rows in ten — that is the data, not a read failure.
 *
 * <p>{@code foundedYear} is an {@code Integer} even though the column is {@code smallint}. A year is
 * naturally an int, and the narrower type bought nothing but a cast that fails — see the row mapper.
 *
 * <p>The array fields are never null: an absent {@code text[]} reads back as an empty list, so no
 * caller has to distinguish "no keywords" from "column was NULL".
 *
 * <p>There is no off-limits flag. A barred company is excluded from every filtered read rather than
 * returned and marked, which is what the Off-limits panel promises in so many words: "completely
 * excluded from your active search results". A flag that is false on every row a caller can
 * ever see is not information.
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
