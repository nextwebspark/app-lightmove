package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A mandate's people filter, the {@code people_filter} jsonb on the strategy and on a saved people
 * search. Every value is one ContactOut's People Search accepts as sent: the vocabulary tokens are the
 * accepted-values sheet's, the rest free text. The match modes hold {@code TitleMatch} and
 * {@code CompanyMatch} tokens, null meaning the vendor's default of current roles only. The two flags are
 * boxed so a document written without them binds — Jackson 3 refuses a missing primitive — and read false.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PeopleFilter(String name, List<String> jobTitles, String titleMatch, Boolean includeRelatedTitles,
                           Boolean recentlyChangedJobs, List<String> excludedJobTitles, List<String> seniorities,
                           List<String> jobFunctions, List<String> skills, List<String> yearsInCurrentRole,
                           List<String> yearsOfExperience, List<String> locations, Integer locationRadius,
                           List<String> companies, List<String> domains, String companyMatch,
                           List<String> excludedCompanies, List<String> companySizes, List<String> industries,
                           List<String> excludedIndustries, List<PeopleLanguage> languages, List<String> education,
                           String keyword, List<String> contactTypes) {

    /** Null-tolerant: a document written before a field existed reads as an unconstrained one. */
    public PeopleFilter {
        includeRelatedTitles = Boolean.TRUE.equals(includeRelatedTitles);
        recentlyChangedJobs = Boolean.TRUE.equals(recentlyChangedJobs);
        jobTitles = listOf(jobTitles);
        excludedJobTitles = listOf(excludedJobTitles);
        seniorities = listOf(seniorities);
        jobFunctions = listOf(jobFunctions);
        skills = listOf(skills);
        yearsInCurrentRole = listOf(yearsInCurrentRole);
        yearsOfExperience = listOf(yearsOfExperience);
        locations = listOf(locations);
        companies = listOf(companies);
        domains = listOf(domains);
        excludedCompanies = listOf(excludedCompanies);
        companySizes = listOf(companySizes);
        industries = listOf(industries);
        excludedIndustries = listOf(excludedIndustries);
        languages = listOf(languages);
        education = listOf(education);
        contactTypes = listOf(contactTypes);
    }

    public static PeopleFilter empty() {
        return new PeopleFilter(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Nothing to search on: ContactOut would answer its whole index, which is not a question. */
    public boolean isEmpty() {
        return isBlank(name) && isBlank(keyword) && jobTitles.isEmpty() && seniorities.isEmpty()
                && jobFunctions.isEmpty() && skills.isEmpty() && yearsInCurrentRole.isEmpty()
                && yearsOfExperience.isEmpty() && locations.isEmpty() && companies.isEmpty() && domains.isEmpty()
                && companySizes.isEmpty() && industries.isEmpty() && languages.isEmpty() && education.isEmpty()
                && !recentlyChangedJobs();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> listOf(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
