package app.lightmove.api.enrichment.peoplesearch.service;

import app.lightmove.api.strategy.constant.CompanyMatch;
import app.lightmove.api.strategy.constant.TitleMatch;
import app.lightmove.api.strategy.model.PeopleFilter;
import app.lightmove.api.strategy.model.PeopleLanguage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A mandate's people filter as ContactOut's People Search body, sending only parameters its API
 * reference lists and nothing for an unconstrained field, so the body — and the cache key hashed from
 * it — is the question and nothing else. Count takes the same body less {@code data_types}.
 */
public final class PeopleFilterBody {

    /** ContactOut's cap on every array parameter. */
    static final int MAX_VALUES = 50;

    private PeopleFilterBody() {
    }

    /**
     * {@code declinedCompanies} join the researcher's own exclusions as current employers only: a
     * mandate that declined a company does not want its people, but may well want its alumni.
     */
    public static Map<String, Object> countBody(PeopleFilter filter, List<String> declinedCompanies) {
        Map<String, Object> body = new LinkedHashMap<>();
        putText(body, "name", filter.name());
        if (TitleMatch.fromValue(filter.titleMatch()) == TitleMatch.PAST) {
            putList(body, "past_job_title", filter.jobTitles());
        } else {
            putList(body, "job_title", filter.jobTitles());
            if (TitleMatch.fromValue(filter.titleMatch()) == TitleMatch.BOTH && !filter.jobTitles().isEmpty()) {
                body.put("current_titles_only", false);
            }
        }
        putFlag(body, "include_related_job_titles", filter.includeRelatedTitles());
        putFlag(body, "recently_changed_jobs", filter.recentlyChangedJobs());
        putList(body, "exclude_job_titles", filter.excludedJobTitles());
        putList(body, "seniority", filter.seniorities());
        putList(body, "job_function", filter.jobFunctions());
        putList(body, "skills", filter.skills());
        putList(body, "years_in_current_role", filter.yearsInCurrentRole());
        putList(body, "years_of_experience", filter.yearsOfExperience());
        putList(body, "location", filter.locations());
        if (filter.locationRadius() != null && filter.locations().size() == 1) {
            body.put("location_radius", filter.locationRadius());
        }
        putList(body, "company", filter.companies());
        putList(body, "domain", filter.domains());
        CompanyMatch companyMatch = CompanyMatch.fromValue(filter.companyMatch());
        if (companyMatch != null && companyMatch != CompanyMatch.CURRENT) {
            body.put("company_filter", companyMatch.value());
        }
        List<String> excluded = distinct(Stream.concat(filter.excludedCompanies().stream(),
                declinedCompanies.stream()).toList());
        if (!excluded.isEmpty()) {
            body.put("exclude_companies", excluded);
            body.put("exclude_companies_filter", "current");
        }
        putList(body, "company_size", filter.companySizes());
        putList(body, "industry", distinct(Stream.concat(filter.industries().stream(),
                filter.excludedIndustries().stream().map(industry -> "NOT " + industry)).toList()));
        if (!filter.languages().isEmpty()) {
            body.put("languages", filter.languages().stream().limit(MAX_VALUES).map(PeopleFilterBody::languageOf)
                    .toList());
        }
        putList(body, "education", filter.education());
        putText(body, "keyword", filter.keyword());
        return body;
    }

    /**
     * The mandate's own filter and nothing of its triage: a page is cached for every workspace under this
     * body, so what one mandate declined must not narrow what another is answered.
     */
    public static Map<String, Object> searchBody(PeopleFilter filter) {
        Map<String, Object> body = countBody(filter, List.of());
        putList(body, "data_types", filter.contactTypes());
        return body;
    }

    private static Map<String, Object> languageOf(PeopleLanguage language) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("language", language.language());
        if (!language.proficiencies().isEmpty()) {
            entry.put("proficiency", language.proficiencies());
        }
        return entry;
    }

    private static void putText(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

    private static void putFlag(Map<String, Object> body, String key, boolean value) {
        if (value) {
            body.put(key, true);
        }
    }

    private static void putList(Map<String, Object> body, String key, List<String> values) {
        if (!values.isEmpty()) {
            body.put(key, values.stream().limit(MAX_VALUES).toList());
        }
    }

    private static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values)).stream().limit(MAX_VALUES).toList();
    }
}
