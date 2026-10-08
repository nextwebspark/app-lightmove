package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.enrichment.company.model.CompanyActivityQuery;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.model.VendorSearchAllowance;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Buys from LinkedIn what the company database came up short on, in the same countries: one search per
 * step while still short. A search finding nobody is not billed and each hit is, so a step buys only
 * what is still missing, never past {@link #MAX_HITS} in all.
 */
@Component
@RequiredArgsConstructor
class LinkedInTopUp {

    static final int MAX_HITS = 10;

    private final ApolloCompanyQueryService market;
    private final CompanyResearch research;

    /** {@code industries} are universe labels, asked as every V2 industry they cover; empty means any. */
    record Step(List<String> industries, String loosening) {}

    record Request(List<String> words, List<String> countries, List<Step> steps, Long minEmployees,
                   Long maxEmployees, int target, Set<String> excludedAccountIds, Set<String> excludedSlugs,
                   int vendorSearches) {}

    boolean isEnabled() {
        return research.isEnabled();
    }

    /** Adds to {@code found} and {@code loosened}; a page the database holds after all is its row. */
    CompanyDiscovery.Discovered topUp(Map<String, CompanyDiscovery.Found> found, List<String> loosened,
                                      Request request) {
        if (request.words().isEmpty()) {
            return new CompanyDiscovery.Discovered(List.copyOf(found.values()), loosened, false, 0);
        }
        Set<String> knownSlugs = new HashSet<>(request.excludedSlugs());
        found.values().stream().map(CompanyDiscovery.Found::slug).filter(Objects::nonNull).forEach(knownSlugs::add);
        List<String> countryCodes = request.countries().stream().map(Countries::codeOf).filter(Objects::nonNull)
                .toList();
        VendorSearchAllowance allowance = new VendorSearchAllowance(request.vendorSearches());
        for (Step step : request.steps()) {
            int missing = Math.min(request.target() - found.size(), MAX_HITS - bought(found));
            if (missing <= 0) {
                break;
            }
            if (step.loosening() != null && !loosened.contains(step.loosening())) {
                loosened.add(step.loosening());
            }
            List<VendorCompanyRecord> pages = research.byActivity(new CompanyActivityQuery(request.words(),
                    countryCodes, linkedInLabelsOf(step.industries()), intOrNull(request.minEmployees()),
                    intOrNull(request.maxEmployees()), List.copyOf(knownSlugs), missing), allowance);
            for (VendorCompanyRecord page : pages) {
                if (found.size() >= request.target() || !knownSlugs.add(page.linkedinSlug())) {
                    continue;
                }
                CompanyDiscovery.Found one = CompanyDiscovery.Found.matched(market, page);
                if (one.accountId() == null || !request.excludedAccountIds().contains(one.accountId())) {
                    found.putIfAbsent(one.key(), one);
                }
            }
        }
        return new CompanyDiscovery.Discovered(List.copyOf(found.values()), loosened, allowance.used() > 0,
                allowance.used());
    }

    private static int bought(Map<String, CompanyDiscovery.Found> found) {
        return (int) found.values().stream().filter(one -> one.page() != null).count();
    }

    private static List<String> linkedInLabelsOf(List<String> industries) {
        return industries.stream().flatMap(industry -> Industries.linkedInLabelsOf(industry).stream())
                .distinct().toList();
    }

    private static Integer intOrNull(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }
}
