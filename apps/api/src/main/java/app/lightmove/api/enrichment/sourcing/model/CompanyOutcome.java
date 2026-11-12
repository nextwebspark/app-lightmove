package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.enrichment.sourcing.constant.SourcingOutcome;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionVerdict;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * What happened at one company: the outcome, the live profiles it bought and those the people cache answered
 * free, how many the live index matched in all (null when unknown), how many people it found — who fit, who had
 * no title on record, who did not fit — and what the profile index answered and the free title checks made.
 * Runs that filed on their own read back with their counts and no more.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyOutcome(UUID triageCompanyId, String companyName, SourcingOutcome outcome,
                             int vendorHits, int cachedHits, Long matched, int found, int fits, int noTitle,
                             int notFit, int indexFound, int titleChecks) {

    public static CompanyOutcome of(SourcingCompany company, SourcingOutcome outcome) {
        return new CompanyOutcome(company.triageCompanyId(), company.companyName(), outcome, 0, 0, null, 0, 0, 0, 0,
                0, 0);
    }

    /** A company whose search failed part-way, keeping the profiles it had bought and read free. */
    public static CompanyOutcome failed(SourcingCompany company, int vendorHits, int cachedHits) {
        return new CompanyOutcome(company.triageCompanyId(), company.companyName(), SourcingOutcome.FAILED, vendorHits,
                cachedHits, null, 0, 0, 0, 0, 0, 0);
    }

    /** The same company read as failed — its results unsaved — with what it spent kept. */
    public CompanyOutcome asFailed() {
        return new CompanyOutcome(triageCompanyId, companyName, SourcingOutcome.FAILED, vendorHits, cachedHits, matched,
                0, 0, 0, 0, indexFound, titleChecks);
    }

    /** A company searched: what was found, judged, and what finding it cost. */
    public static CompanyOutcome searched(SourcingCompany company, FoundPeople found, List<Suggestion> judged) {
        int fits = count(judged, SuggestionVerdict.FITS);
        SourcingOutcome outcome = judged.isEmpty() ? SourcingOutcome.NO_HITS
                : fits > 0 ? SourcingOutcome.FOUND : SourcingOutcome.NOTHING_FIT;
        return new CompanyOutcome(company.triageCompanyId(), company.companyName(), outcome, found.credits(),
                found.cached(), found.matched(), judged.size(), fits, count(judged, SuggestionVerdict.NO_TITLE),
                count(judged, SuggestionVerdict.NOT_FIT), found.indexFound(), found.titleChecks());
    }

    private static int count(List<Suggestion> judged, SuggestionVerdict verdict) {
        return (int) judged.stream().filter(suggestion -> suggestion.verdict() == verdict).count();
    }
}
