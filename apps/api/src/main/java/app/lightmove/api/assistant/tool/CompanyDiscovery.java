package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.company.model.CompanyActivityQuery;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Finds companies by who they are like or what they do — the company database first, because it is
 * free, and LinkedIn through Bright Data only for what the database comes up short on. "Like" is the
 * same niche (rare keywords in common) first, then the same sector, then a similar headcount; the last
 * two are given up one at a time until enough are found, and the country never is.
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

    /** Each LinkedIn hit is billed, so a top-up buys only what is missing, and never more than this. */
    static final int MAX_LINKEDIN_HITS = 10;

    /** Words of a niche keyword that say nothing about the niche on their own. */
    private static final Set<String> GENERIC_WORDS = Set.of("services", "service", "solutions", "company",
            "companies", "group", "business", "businesses", "customer", "customers", "experience", "brand", "brands",
            "market", "markets", "digital", "management", "family", "consumer", "consumers", "products", "product",
            "international", "local", "online", "luxury", "premium", "high", "end", "high-end", "retail", "trading",
            "quality", "global", "regional", "middle", "east", "innovation", "development", "industry");

    private final ApolloCompanyQueryService market;
    private final CompanyResearch research;
    private final SectorTaxonomy taxonomy;

    /** A company the database holds ({@code row}) or one only LinkedIn knows ({@code page}). */
    record Found(CompanyRow row, VendorCompanyRecord page, List<String> sharedNiche) {

        static Found of(CompanyRow row) {
            return new Found(row, null, List.of());
        }

        static Found of(VendorCompanyRecord page) {
            return new Found(null, page, List.of());
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
            Found one = market.matchEmployer(page.linkedinSlug(), page.companyName())
                    .map(Found::of)
                    .orElseGet(() -> Found.of(page));
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
        return research.recordOf(slug).map(page -> market.matchEmployer(page.linkedinSlug(), page.companyName())
                .map(Found::of)
                .orElseGet(() -> Found.of(page)));
    }

    /** The anchor's niche as the database spells it, or as its LinkedIn page does where the database has no word for it. */
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

        if (found.size() >= target || !research.isEnabled()) {
            return new Discovered(List.copyOf(found.values()), loosened, false, 0);
        }
        Long minEmployees = employees == null ? null : employees / 10L;
        Long maxEmployees = employees == null ? null : employees * 10L;
        Set<String> skipSlugs = new HashSet<>();
        if (anchor.slug() != null) {
            skipSlugs.add(anchor.slug());
        }
        List<LinkedInStep> linkedInSteps = new ArrayList<>();
        List<String> sectorOnLinkedIn = linkedInLabelsOf(sector);
        if (!sectorOnLinkedIn.isEmpty()) {
            linkedInSteps.add(new LinkedInStep(sectorOnLinkedIn, null));
        }
        linkedInSteps.add(new LinkedInStep(List.of(), sector.isEmpty() ? null : LOOKED_BEYOND_SECTOR));
        return topUpFromLinkedIn(found, nicheWords(nicheOf(anchor)), countries, linkedInSteps, minEmployees,
                maxEmployees, target, excluded, skipSlugs, loosened, vendorSearches);
    }

    Discovered byActivity(List<String> words, List<String> countries, List<String> industries, Long minEmployees,
                          Long maxEmployees, int target, Collection<String> offLimits, int vendorSearches) {
        List<String> stems = words.stream()
                .filter(Objects::nonNull)
                .flatMap(word -> Stream.of(word.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")))
                .map(CompanyDiscovery::singular)
                .filter(word -> word.length() >= 3)
                .distinct()
                .limit(4)
                .toList();
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
        if (found.size() >= target || !research.isEnabled()) {
            return new Discovered(List.copyOf(found.values()), List.of(), false, 0);
        }
        return topUpFromLinkedIn(found, stems, countries,
                List.of(new LinkedInStep(linkedInLabelsOf(industries), null)), minEmployees, maxEmployees, target,
                Set.copyOf(offLimits), Set.of(), new ArrayList<>(), vendorSearches);
    }

    /**
     * What the database came up short on, bought from LinkedIn in the same countries, one search per step
     * while still short — the sector first, then without it. A search finding nobody is not billed; each
     * hit is, so each step buys only what is still missing. A page the database holds after all is its
     * row; one already found, off limits or the anchor itself is dropped.
     */
    private Discovered topUpFromLinkedIn(Map<String, Found> found, List<String> words, List<String> countries,
                                         List<LinkedInStep> steps, Long minEmployees, Long maxEmployees,
                                         int target, Set<String> excluded, Set<String> skipSlugs,
                                         List<String> loosened, int vendorSearches) {
        if (words.isEmpty()) {
            return new Discovered(List.copyOf(found.values()), loosened, false, 0);
        }
        Set<String> knownSlugs = new HashSet<>(skipSlugs);
        found.values().stream().map(Found::slug).filter(Objects::nonNull).forEach(knownSlugs::add);
        List<String> countryCodes = countries.stream().map(Countries::codeOf).filter(Objects::nonNull).toList();
        VendorSearchAllowance allowance = new VendorSearchAllowance(vendorSearches);
        for (LinkedInStep step : steps) {
            int missing = Math.min(target - found.size(), MAX_LINKEDIN_HITS - boughtFrom(found));
            if (missing <= 0) {
                break;
            }
            if (step.loosening() != null && !loosened.contains(step.loosening())) {
                loosened.add(step.loosening());
            }
            List<VendorCompanyRecord> pages = research.byActivity(new CompanyActivityQuery(words, countryCodes,
                    step.industries(), intOrNull(minEmployees), intOrNull(maxEmployees), List.copyOf(knownSlugs),
                    missing), allowance);
            for (VendorCompanyRecord page : pages) {
                if (found.size() >= target || !knownSlugs.add(page.linkedinSlug())) {
                    continue;
                }
                Found one = market.matchEmployer(page.linkedinSlug(), page.companyName())
                        .map(Found::of)
                        .orElseGet(() -> Found.of(page));
                if (one.accountId() != null && excluded.contains(one.accountId())) {
                    continue;
                }
                found.putIfAbsent(one.key(), one);
            }
        }
        return new Discovered(List.copyOf(found.values()), loosened, allowance.used() > 0, allowance.used());
    }

    private static int boughtFrom(Map<String, Found> found) {
        return (int) found.values().stream().filter(one -> one.page() != null).count();
    }

    /** LinkedIn files a company under V2's finer industries, so a universe label is asked as every one it covers. */
    private static List<String> linkedInLabelsOf(List<String> industries) {
        return industries.stream().flatMap(industry -> Industries.linkedInLabelsOf(industry).stream()).distinct().toList();
    }

    /**
     * Up to four single words for a LinkedIn search, from the niche keywords — the words most of them
     * share, since "watch" in "luxury watches", "watch retail" and "watch repair" is the niche itself.
     */
    static List<String> nicheWords(List<String> niche) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String keyword : niche) {
            Set<String> words = new LinkedHashSet<>();
            for (String word : keyword.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                String stem = singular(word);
                if (stem.length() >= 4 && !GENERIC_WORDS.contains(stem) && !GENERIC_WORDS.contains(word)) {
                    words.add(stem);
                }
            }
            words.forEach(word -> counts.merge(word, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(4)
                .toList();
    }

    /** "watches" → "watch", "distributors" → "distributor": LinkedIn's {@code includes} then finds both. */
    static String singular(String word) {
        if (word.length() > 4 && word.matches(".*(ch|sh|x|ss)es")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 4 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
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

    private static Integer intOrNull(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private record Step(List<String> industries, Long minEmployees, Long maxEmployees, int minShared,
                        String loosening) {}

    private record LinkedInStep(List<String> industries, String loosening) {}
}
