package app.lightmove.api.enrichment.sourcing.service;

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
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * What a search would reach before anything is spent, all of it free: the live index's counts, held for minutes,
 * and the people on file. Each company is counted on its own; each level and suggested word across the companies
 * at once, so a panel of ten companies costs a few dozen counts, not hundreds.
 */
@Service
public class SourcingCounts {

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
        List<CompanyCount> perCompany = chosen.stream()
                .map(company -> companyCount(company, criteria, counted))
                .toList();
        List<SourcingCompany> searchable = chosen.stream().filter(company -> company.linkedinSlug() != null).toList();
        List<SearchedEmployer> employers = searchable.stream().map(SourcingCompany::employer).toList();
        List<String> countries = countriesOf(searchable, criteria);
        List<LevelCount> levels = Arrays.stream(SearchLevel.values())
                .map(level -> new LevelCount(level, counted && !employers.isEmpty()
                        ? live.countAcross(employers, words.atLevel(level), countries) : 0))
                .toList();
        List<WordCount> suggested = words.suggestions().stream()
                .map(word -> new WordCount(word, counted && !employers.isEmpty()
                        ? live.countAcross(employers, words.withTermAlone(word), countries) : 0))
                .toList();
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
