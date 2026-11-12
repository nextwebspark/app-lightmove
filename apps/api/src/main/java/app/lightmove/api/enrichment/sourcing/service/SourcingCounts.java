package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.core.concurrent.service.ThreadContextPropagation;
import app.lightmove.api.core.config.ExecutiveSourcingSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCountsResponse;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCountsResponse.CompanyCount;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCountsResponse.LevelCount;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCountsResponse.WordCount;
import app.lightmove.api.enrichment.sourcing.model.SearchCriteria;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * What a search would reach before anything is spent, all of it free: the live index's counts, held for minutes,
 * and the people on file. Each company is counted on its own; each level and suggested word across the companies
 * at once, so a panel of ten companies costs a few dozen counts, not hundreds. The counts are asked side by side,
 * {@link #COUNTS_AT_ONCE} at a time — one after another they kept the panel blank for half a minute — which stays
 * under the live index's count pacing.
 */
@Service
public class SourcingCounts {

    static final int COUNTS_AT_ONCE = 6;

    private final LiveProfiles live;
    private final PeopleCache cache;
    private final SourcingCompanies companies;
    private final ExecutiveSourcingSettings settings;

    public SourcingCounts(LiveProfiles live, PeopleCache cache, SourcingCompanies companies,
                          LightMoveProperties properties) {
        this.live = live;
        this.cache = cache;
        this.companies = companies;
        this.settings = properties.enrichment().sourcing();
    }

    public SourcingCountsResponse count(UUID workspaceId, UUID projectId, List<UUID> companyIds,
                                        SearchCriteria criteria) {
        List<SourcingCompany> chosen = companies.chosen(workspaceId, projectId, companyIds,
                settings.maxCompaniesPerRun());
        SearchWords words = criteria.words();
        boolean counted = live.isOffered() && words.isSearchable();
        List<SourcingCompany> searchable = chosen.stream().filter(company -> company.linkedinSlug() != null).toList();
        List<SearchedEmployer> employers = searchable.stream().map(SourcingCompany::employer).toList();
        List<String> countries = countriesOf(searchable, criteria);
        boolean acrossCounted = counted && !employers.isEmpty();
        List<CompanyCount> perCompany;
        List<LevelCount> levels;
        List<WordCount> suggested;
        try (ExecutorService pool = Executors.newFixedThreadPool(COUNTS_AT_ONCE, Thread.ofVirtual().factory())) {
            List<Future<CompanyCount>> companyCounts = chosen.stream()
                    .map(company -> submit(pool, () -> companyCount(company, criteria, counted)))
                    .toList();
            List<Future<LevelCount>> levelCounts = Arrays.stream(SearchLevel.values())
                    .map(level -> submit(pool, () -> new LevelCount(level, acrossCounted
                            ? live.countAcross(employers, words.atLevel(level), countries) : 0)))
                    .toList();
            List<Future<WordCount>> wordCounts = words.suggestions().stream()
                    .map(word -> submit(pool, () -> new WordCount(word, acrossCounted
                            ? live.countAcross(employers, words.withTermAlone(word), countries) : 0)))
                    .toList();
            try {
                perCompany = joined(companyCounts);
                levels = joined(levelCounts);
                suggested = joined(wordCounts);
            } catch (RuntimeException failed) {
                pool.shutdownNow();
                throw failed;
            }
        }
        return new SourcingCountsResponse(counted, perCompany, levels, suggested,
                perCompany.stream().mapToLong(CompanyCount::match).sum(),
                perCompany.stream().mapToLong(CompanyCount::onFile).sum(),
                perCompany.stream().mapToLong(CompanyCount::toBuy).sum());
    }

    private CompanyCount companyCount(SourcingCompany company, SearchCriteria criteria, boolean counted) {
        if (company.linkedinSlug() == null || !counted) {
            return new CompanyCount(company.triageCompanyId(), company.companyName(), company.linkedinSlug() != null,
                    0, 0, 0);
        }
        List<String> countries = criteria.countriesFor(company);
        long match = live.count(company.employer(), TitleTier.of(criteria.words()), countries);
        long onFile = Math.min(match, cache.onFile(live, company.employer(), criteria.words(), countries));
        long toBuy = Math.min(criteria.perCompany(), match - onFile);
        return new CompanyCount(company.triageCompanyId(), company.companyName(), true, match, onFile, toBuy);
    }

    private static <T> Future<T> submit(ExecutorService pool, Callable<T> count) {
        return pool.submit(ThreadContextPropagation.wrap(count));
    }

    /** In the order asked; a count that failed fails the whole answer, as it did when they were asked in turn. */
    private static <T> List<T> joined(List<Future<T>> futures) {
        return futures.stream().map(SourcingCounts::joinedOne).toList();
    }

    private static <T> T joinedOne(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Counting was interrupted", interrupted);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(failed.getCause());
        }
    }

    /** The search's countries and each company's own; anywhere when the search names none. */
    private static List<String> countriesOf(List<SourcingCompany> searchable, SearchCriteria criteria) {
        if (criteria.countryCodes().isEmpty()) {
            return List.of();
        }
        return Stream.concat(criteria.countryCodes().stream(),
                        searchable.stream().map(SourcingCompany::countryCode).filter(code -> code != null))
                .distinct()
                .toList();
    }
}
