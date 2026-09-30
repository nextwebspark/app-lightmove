package app.lightmove.api.assistant.tool;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;

/**
 * What one name the model remembered turned out to be. {@code UNIVERSE} carries an Apollo account id,
 * {@code RESEARCHED} a LinkedIn slug; every figure is the universe's or the provider's, never the
 * model's. {@code operates} is the brand this company runs locally, and {@code global} marks a
 * company found as its own headquarters page rather than in the country asked about.
 * {@code mandateStage} is the stage this mandate already filed it at, null when it has not.
 */
public record NamedCompanyFinding(String askedName, Status status, String apolloAccountId,
                                  String linkedinSlug, String companyName, String country,
                                  String industry, String city, Integer employees, Integer foundedYear,
                                  String about, String operates, boolean global, String mandateStage) {

    public enum Status { UNIVERSE, RESEARCHED, OFF_LIMITS, UNVERIFIED, NOT_CHECKED }

    static NamedCompanyFinding unresolved(String askedName, Status status) {
        return new NamedCompanyFinding(askedName, status, null, null, null, null, null, null, null, null,
                null, null, false, null);
    }

    NamedCompanyFinding inMandateAs(TriageCompanyStatus stage) {
        return new NamedCompanyFinding(askedName, status, apolloAccountId, linkedinSlug, companyName, country,
                industry, city, employees, foundedYear, about, operates, global,
                stage == null ? null : stage.value());
    }
}
