package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyRow;

/**
 * One company of the market, as much of it as the model is worth showing.
 *
 * <p>Narrower than {@code CompanyRow} on purpose. Every field handed back is spent twice — once in
 * the model's context window and again in the bill — and the twenty-seven a universe row carries
 * are a grid's worth, not an answer's. The account id is here because it is the key a later
 * proposal has to name in order to be filed through the door that already exists.
 */
public record MarketCompanySummary(String apolloAccountId, String companyName, String industry,
                                   String country, String city, Integer employees,
                                   Integer foundedYear, String website) {

    public static MarketCompanySummary of(CompanyRow row) {
        return new MarketCompanySummary(row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCountry(), row.companyCity(), row.numEmployees(), row.foundedYear(),
                row.website());
    }
}
