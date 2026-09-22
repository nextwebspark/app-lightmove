package app.lightmove.api.companydiscovery.dto;

import app.lightmove.api.companydiscovery.constant.DiscoverySource;
import app.lightmove.api.companydiscovery.model.DiscoveredCandidate;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;

/**
 * One row of an answer, as the grid draws it.
 *
 * <p><b>There is no public constructor, and that is the invariant rather than a style choice.</b>
 * Every figure on this record comes from a {@link CompanyRow} or a {@link CapturedCompanyDetails} —
 * from a record, in other words — and the three factories below are the only ways to build one. A
 * line taking a headcount off a {@link DiscoveredCandidate} cannot be written, because
 * {@link #unresolved} is handed the candidate and passes null for every figure. Discipline would
 * have needed a reviewer to notice; this needs a compiler.
 *
 * <p>{@code source} says where the figures came from and {@code alreadyInMandate} says whether this
 * mandate already holds the company. Two fields, because they are independent facts.
 */
public record DiscoveredCompanyDto(
        String ref,
        String source,
        boolean alreadyInMandate,
        boolean unresolved,
        String apolloAccountId,
        String companyName,
        String industry,
        String companyCountry,
        String companyCity,
        Integer numEmployees,
        Long annualRevenue,
        String website,
        String companyLinkedinUrl,
        Integer foundedYear,
        String logoUrl,
        String shortDescription,
        String sourceUrl,
        String reason,
        Integer fit) {

    /** The universe carries it: the row is the market's, down to the name it publishes. */
    public static DiscoveredCompanyDto fromUniverse(String ref, CompanyRow row,
                                                    DiscoveredCandidate candidate,
                                                    boolean alreadyInMandate) {
        return new DiscoveredCompanyDto(ref, DiscoverySource.UNIVERSE.value(), alreadyInMandate,
                false, row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCountry(), row.companyCity(), row.numEmployees(), row.annualRevenue(),
                row.website(), row.companyLinkedinUrl(), row.foundedYear(), row.logoUrl(),
                row.shortDescription(), candidate.sourceUrl(), candidate.reason(), candidate.fit());
    }

    /** No universe row, but a vendor holds the page. The figures are the vendor's. */
    public static DiscoveredCompanyDto fromVendor(String ref, CapturedCompanyDetails facts,
                                                  DiscoveredCandidate candidate,
                                                  boolean alreadyInMandate) {
        return new DiscoveredCompanyDto(ref, DiscoverySource.RESEARCHED.value(), alreadyInMandate,
                false, null, facts.companyName(), facts.industry(), facts.companyCountry(),
                facts.companyCity(), facts.numEmployees(), facts.annualRevenue(), facts.website(),
                facts.companyLinkedinUrl(), facts.foundedYear(), facts.logoUrl(),
                facts.shortDescription(), candidate.sourceUrl(), candidate.reason(),
                candidate.fit());
    }

    /**
     * Nobody holds a record of it. The name the model gave, the page it cited, and every figure
     * null — which is the whole point: a blank cell says "we do not know", and a plausible number
     * would not.
     *
     * <p>The candidate's own {@code websiteUrl} is deliberately dropped. It was a lookup key and it
     * failed to find anything, so rendering it as this company's website would publish the one
     * unchecked claim this record exists to keep out.
     */
    public static DiscoveredCompanyDto unresolved(String ref, DiscoveredCandidate candidate,
                                                  boolean alreadyInMandate) {
        return new DiscoveredCompanyDto(ref, DiscoverySource.WEB.value(), alreadyInMandate, true,
                null, candidate.companyName(), null, null, null, null, null, null,
                candidate.linkedinUrl(), null, null, null, candidate.sourceUrl(),
                candidate.reason(), candidate.fit());
    }
}
