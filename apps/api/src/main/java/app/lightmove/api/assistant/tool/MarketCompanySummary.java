package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyRow;

/**
 * One market company as the model sees it — narrower than {@code CompanyRow}, since every field costs context and money.
 * The account id is the key a later proposal files by; {@code mandateStage} is null when this mandate has not filed it.
 */
public record MarketCompanySummary(String apolloAccountId, String companyName, String industry,
                                   String country, String city, Integer employees,
                                   Integer foundedYear, String website, String mandateStage) {

    public static MarketCompanySummary of(CompanyRow row) {
        return new MarketCompanySummary(row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCountry(), row.companyCity(), row.numEmployees(), row.foundedYear(),
                row.website(), null);
    }

    MarketCompanySummary inMandateAs(String stageToken) {
        return new MarketCompanySummary(apolloAccountId, companyName, industry, country, city, employees,
                foundedYear, website, stageToken);
    }
}
