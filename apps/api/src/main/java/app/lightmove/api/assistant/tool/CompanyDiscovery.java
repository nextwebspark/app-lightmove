package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.model.VendorSearchAllowance;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.ScoredCompanyRow;
import app.lightmove.api.strategy.model.SimilarityScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Finds companies by who they are like or what they do: the company database first, because it is free,
 * then {@link LinkedInTopUp} for what it comes up short on. Criteria are given up one at a time until
 * enough are found; the country never is.
 */
@Component
@RequiredArgsConstructor
class CompanyDiscovery {

    static final int MAX_CANDIDATES = 5;

    /** A namesake this much smaller than the biggest match is a shell company, not a choice to offer. */
    static final double NAMESAKE_SHARE = 0.1;

    /** A company must share this many of the anchor's niche keywords; one alone is often a coincidence. */
    static final int MIN_SHARED_NICHE = 2;

    static final int NICHE_SHOWN = 10;

    static final String LOOKED_BEYOND_SECTOR = "looked beyond its sector";

    private final ApolloCompanyQueryService market;
    private final CompanyResearch research;
    private final SectorTaxonomy taxonomy;
    private final LinkedInTopUp linkedIn;

    /** A company the database holds ({@code row}) or one only LinkedIn knows ({@code page}). */
    record Found(CompanyRow row, VendorCompanyRecord page, List<String> sharedNiche) {

        static Found of(CompanyRow row) {
            return new Found(row, null, List.of());
        }

        static Found of(VendorCompanyRecord page) {
            return new Found(null, page, List.of());
        }

        /** A LinkedIn page the database holds after all is its row. */
        static Found matched(ApolloCompanyQueryService market, VendorCompanyRecord page) {
            return market.matchEmployer(page.linkedinSlug(), page.companyName())
                    .map(Found::of)
                    .orElseGet(() -> of(page));
        }

        String accountId() {
            return row == null ? null : row.apolloAccountId();
        }

        String slug() {
            return row != null ? LinkedInUrls.companySlugOrNull(row.companyLinkedinUrl()) : page.linkedinSlug();
        }

        String key() {
            return row != null ? row.apolloAccountId() : page.linkedinSlug();
        }

        String name() {
            return row != null ? row.companyName() : page.companyName();
        }

        Integer employees() {
            return row != null ? row.numEmployees() : page.employeesInLinkedin();
        }

        String country() {
            return row != null ? row.companyCountry() : page.companyCountry();
        }

        /** The universe's industry label; a LinkedIn page's own is canonicalised the way a capture files it. */
        String industry() {
            return row != null ? row.industry()
                    : page.asCapturedDetails().map(CapturedCompanyDetails::industry).orElse(null);
        }

        List<String> keywords() {
            List<String> keywords = row != null ? row.keywords() : page.keywords();
            return keywords == null ? List.of() : keywords;
        }

        CapturedCompanyDetails details() {
            return page == null ? null : page.asCapturedDetails().orElse(null);
        }
    }

    record Identified(List<Found> candidates, int vendorSearches) {}

    record Discovered(List<Found> companies, List<String> loosened, boolean searchedLinkedIn, int vendorSearches) {}

    Identified identify(String name, String country, int vendorSearches) {
        List<String> words = CompanyResearch.distinctiveNameWords(name);
        String countryName = Countries.nameOf(country);
        List<CompanyRow> rows = market.namedLike(words, countryName, MAX_CANDIDATES);
        if (rows.isEmpty() && countryName != null) {
            rows = market.namedLike(words, null, MAX_CANDIDATES);
        }
        if (!rows.isEmpty()) {
            return new Identified(plausible(rows.stream().map(Found::of).toList()), 0);
        }
        VendorSearchAllowance allowance = new VendorSearchAllowance(vendorSearches);
        Map<String, Found> found = new LinkedHashMap<>();
        for (VendorCompanyRecord page : research.pagesNamed(name, country, allowance)) {
            Found one = Found.matched(market, page);
            found.putIfAbsent(one.key(), one);
        }
        return new Identified(plausible(List.copyOf(found.values())), allowance.used());
    }

    /** By account id where the database holds it, else by the LinkedIn slug a lookup or search reported. */
    Optional<Found> anchorOf(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return Optional.empty();
        }
        String id = companyId.strip();
        List<CompanyRow> held = market.byAccountIds(List.of(id));
        if (!held.isEmpty()) {
            return Optional.of(Found.of(held.getFirst()));
        }
        String slug = Optional.ofNullable(LinkedInUrls.companySlugOrNull(id)).orElse(id.toLowerCase(Locale.ROOT));
        return research.recordOf(slug).map(page -> Found.matched(market, page));
    }

    /** As the database spells it, or as the LinkedIn page does where the database has no word for it. */
    List<String> nicheOf(Found company) {
        List<String> distinctive = market.distinctiveKeywords(company.keywords(), NICHE_SHOWN);
        return distinctive.isEmpty() ? company.keywords().stream().limit(NICHE_SHOWN).toList() : distinctive;
    }

    Discovered similarTo(Found anchor, List<String> countries, int target, Collection<String> offLimits,
                         int vendorSearches) {
        List<String> keywords = anchor.keywords();
        List<String> sector = taxonomy.sectorOf(anchor.industry());
        Integer employees = anchor.employees();
        List<Step> steps = new ArrayList<>();
        if (!sector.isEmpty() && employees != null) {
            steps.add(new Step(sector, employees / 4L, employees * 4L, MIN_SHARED_NICHE, null));
            steps.add(new Step(sector, employees / 10L, employees * 10L, MIN_SHARED_NICHE,
                    "widened the size to between a tenth and ten times its headcount"));
        }
        if (!sector.isEmpty()) {
            steps.add(new Step(sector, null, null, MIN_SHARED_NICHE,
                    employees == null ? null : "dropped the size limit"));
        }
        steps.add(new Step(List.of(), null, null, MIN_SHARED_NICHE, sector.isEmpty() ? null : LOOKED_BEYOND_SECTOR));
        steps.add(new Step(sector, null, null, 1, "took companies sharing a single niche keyword"));

        Set<String> excluded = new HashSet<>(offLimits);
        if (anchor.accountId() != null) {
            excluded.add(anchor.accountId());
        }
        Map<String, Found> found = new LinkedHashMap<>();
        List<String> loosened = new ArrayList<>();
        for (Step step : steps) {
            if (found.size() >= target) {
                break;
            }
            if (step.loosening() != null) {
                loosened.add(step.loosening());
            }
            Set<String> skip = new HashSet<>(excluded);
            skip.addAll(found.keySet());
            market.similarTo(new SimilarityScope(keywords, countries, step.industries(), step.minEmployees(),
                            step.maxEmployees(), List.copyOf(skip), step.minShared(), target - found.size()))
                    .forEach(scored -> found.putIfAbsent(scored.row().apolloAccountId(), scored(scored)));
        }

        if (found.size() >= target || !linkedIn.isEnabled()) {
            return new Discovered(List.copyOf(found.values()), loosened, false, 0);
        }
        List<LinkedInTopUp.Step> linkedInSteps = new ArrayList<>();
        if (sector.stream().anyMatch(industry -> !Industries.linkedInLabelsOf(industry).isEmpty())) {
            linkedInSteps.add(new LinkedInTopUp.Step(sector, null));
        }
        linkedInSteps.add(new LinkedInTopUp.Step(List.of(), sector.isEmpty() ? null : LOOKED_BEYOND_SECTOR));
        Set<String> anchorSlug = anchor.slug() == null ? Set.of() : Set.of(anchor.slug());
        return linkedIn.topUp(found, loosened, new LinkedInTopUp.Request(NicheWords.of(nicheOf(anchor)), countries,
                linkedInSteps, employees == null ? null : employees / 10L, employees == null ? null : employees * 10L,
                target, excluded, anchorSlug, vendorSearches));
    }

    Discovered byActivity(List<String> words, List<String> countries, List<String> industries, Long minEmployees,
                          Long maxEmployees, int target, Collection<String> offLimits, int vendorSearches) {
        List<String> stems = NicheWords.stemsOf(words);
        if (stems.isEmpty()) {
            return new Discovered(List.of(), List.of(), false, 0);
        }
        List<String> keywords = stems.stream()
                .flatMap(stem -> market.keywordSuggestions(stem, 40, 2).stream().map(facet -> facet.label()))
                .distinct()
                .toList();
        Map<String, Found> found = new LinkedHashMap<>();
        market.similarTo(new SimilarityScope(keywords, countries, industries, minEmployees, maxEmployees,
                        List.copyOf(offLimits), 1, target))
                .forEach(scored -> found.putIfAbsent(scored.row().apolloAccountId(), scored(scored)));
        if (found.size() >= target || !linkedIn.isEnabled()) {
            return new Discovered(List.copyOf(found.values()), List.of(), false, 0);
        }
        return linkedIn.topUp(found, new ArrayList<>(), new LinkedInTopUp.Request(stems, countries,
                List.of(new LinkedInTopUp.Step(industries, null)), minEmployees, maxEmployees, target,
                Set.copyOf(offLimits), Set.of(), vendorSearches));
    }

    /** Namesakes a fraction of the biggest match's size are dropped; one with no headcount stays. */
    private static List<Found> plausible(List<Found> candidates) {
        int biggest = candidates.stream().map(Found::employees).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0);
        return candidates.stream()
                .filter(one -> one.employees() == null || one.employees() >= biggest * NAMESAKE_SHARE)
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private static Found scored(ScoredCompanyRow scored) {
        return new Found(scored.row(), null, scored.sharedKeywords());
    }

    private record Step(List<String> industries, Long minEmployees, Long maxEmployees, int minShared,
                        String loosening) {}

}
