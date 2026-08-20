package app.lightmove.api.strategy.model;

/**
 * One row of the Apollo company universe, as read back for a filtered list.
 *
 * <p>Wider than the Strategy table's eight columns, because the table lets a user choose which
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
 * <p>There is no off-limits flag. A barred company is excluded from every filtered read rather than
 * returned and marked, which is what the Off-limits panel promises in so many words: "completely
 * excluded from your active sourcing search results". A flag that is false on every row a caller can
 * ever see is not information.
 */
public record CompanyRow(String apolloAccountId, String companyName, String industry,
                          String companyCountry, String companyCity, Integer numEmployees,
                          Long annualRevenue, String website, String companyLinkedinUrl,
                          String logoUrl, String shortDescription, Integer foundedYear) {}
