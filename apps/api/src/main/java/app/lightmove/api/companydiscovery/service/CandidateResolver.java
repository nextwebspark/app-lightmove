package app.lightmove.api.companydiscovery.service;

import app.lightmove.api.companydiscovery.dto.DiscoveredCompanyDto;
import app.lightmove.api.companydiscovery.model.DiscoveredCandidate;
import app.lightmove.api.companydiscovery.model.HeldCompanies;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.WebsiteDomain;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns what a model proposed into what the grid may draw.
 *
 * <p>This is where the feature's one rule is enforced: <b>every figure comes from a record.</b> The
 * model's own answer contributes a name to look the company up by, the pages to look it up on, a
 * reason to show the consultant, and a relevance score. Nothing else survives the journey — see
 * {@link DiscoveredCompanyDto}, which has no constructor that could carry one.
 */
@Service
@RequiredArgsConstructor
public class CandidateResolver {

    private final ApolloCompanyQueryService companies;
    private final CompanyResearch research;

    /**
     * Resolves each candidate, first hit wins, and drops the duplicates a model produces when it
     * names one company twice under two spellings.
     */
    public List<DiscoveredCompanyDto> resolve(List<DiscoveredCandidate> candidates,
                                              HeldCompanies held) {
        List<DiscoveredCompanyDto> rows = new ArrayList<>(candidates.size());
        Set<String> seen = new HashSet<>();
        for (DiscoveredCandidate candidate : candidates) {
            if (candidate == null || candidate.companyName() == null
                    || candidate.companyName().isBlank()) {
                continue;
            }
            String ref = refFor(candidate);
            // Two rows for one company would file it twice and count it twice in the answer's own
            // total, which is the number a consultant reads as "how much is out there".
            if (!seen.add(ref)) {
                continue;
            }
            rows.add(resolveOne(ref, candidate, held));
        }
        return rows;
    }

    private DiscoveredCompanyDto resolveOne(String ref, DiscoveredCandidate candidate,
                                            HeldCompanies held) {
        String slug = LinkedInUrls.companySlugOrNull(candidate.linkedinUrl());

        // 1. The LinkedIn page the model cited. The strongest key: it is a page it actually read,
        //    and matchEmployer's slug tier is exact.
        Optional<CompanyRow> universe = companies.matchEmployer(slug, null);

        // 2. The homepage. Weaker than a cited page — a plausible domain is easy to produce — but
        //    exact all the same, and unique-or-nothing.
        if (universe.isEmpty()) {
            universe = companies.matchByDomain(candidate.websiteUrl());
        }

        // 3. The name, only when the universe holds exactly one company under it.
        if (universe.isEmpty()) {
            universe = companies.matchEmployer(null, candidate.companyName());
        }

        if (universe.isPresent()) {
            return DiscoveredCompanyDto.fromUniverse(ref, universe.get(), candidate,
                    holdsUniverseRow(held, universe.get()));
        }

        // 4. The paid step, and only for a real LinkedIn slug: LinkedInUrls is the billing gate here
        //    exactly as it is for contact lookup, and CompanyResearch's cache and stored misses mean
        //    the same page is not bought twice across the whole platform.
        if (slug != null) {
            Optional<CapturedCompanyDetails> vendor = research.of(slug);
            if (vendor.isPresent()) {
                CapturedCompanyDetails facts = vendor.get();
                // A slug the universe does not carry may still be a company it carries under a
                // domain — the vendor's own website is a better key than the model's guess was.
                Optional<CompanyRow> byVendorDomain = companies.matchByDomain(facts.website());
                if (byVendorDomain.isPresent()) {
                    return DiscoveredCompanyDto.fromUniverse(ref, byVendorDomain.get(), candidate,
                            holdsUniverseRow(held, byVendorDomain.get()));
                }
                return DiscoveredCompanyDto.fromVendor(ref, facts, candidate,
                        holdsVendorRow(held, facts, slug));
            }
        }

        // 5. Nobody holds a record. The name and the page, and no figures at all.
        return DiscoveredCompanyDto.unresolved(ref, candidate,
                held.holdsSlug(slug) || held.holdsName(lower(candidate.companyName())));
    }

    private static boolean holdsUniverseRow(HeldCompanies held, CompanyRow row) {
        return held.holdsApolloId(row.apolloAccountId())
                || held.holdsName(lower(row.companyName()))
                || held.holdsDomain(WebsiteDomain.of(row.website()))
                || held.holdsSlug(LinkedInUrls.companySlugOrNull(row.companyLinkedinUrl()));
    }

    private static boolean holdsVendorRow(HeldCompanies held, CapturedCompanyDetails facts,
                                          String slug) {
        return held.holdsSlug(slug)
                || held.holdsName(lower(facts.companyName()))
                || held.holdsDomain(WebsiteDomain.of(facts.website()));
    }

    /**
     * The row's identity inside one answer, and what the accept request names. The slug where there
     * is one, because two spellings of a name are the duplicate this is here to collapse.
     */
    private static String refFor(DiscoveredCandidate candidate) {
        String slug = LinkedInUrls.companySlugOrNull(candidate.linkedinUrl());
        return slug != null ? "li:" + slug : "name:" + lower(candidate.companyName());
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
