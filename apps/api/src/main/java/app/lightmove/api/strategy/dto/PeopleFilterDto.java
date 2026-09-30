package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.PeopleFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The People sidebar's whole selection, both directions. An absent list is an empty one. Fifty per list
 * is ContactOut's own cap; the lengths catch client bugs, and a title may be a Boolean expression.
 */
public record PeopleFilterDto(
        @Size(max = 120) String name,
        @Size(max = 50, message = "Too many job titles") List<@Size(max = 500) String> jobTitles,
        @Size(max = 16) String titleMatch,
        Boolean includeRelatedTitles,
        Boolean recentlyChangedJobs,
        @Size(max = 50, message = "Too many excluded titles") List<@Size(max = 120) String> excludedJobTitles,
        @Size(max = 50, message = "Too many seniorities") List<@Size(max = 64) String> seniorities,
        @Size(max = 50, message = "Too many job functions") List<@Size(max = 64) String> jobFunctions,
        @Size(max = 50, message = "Too many skills") List<@Size(max = 500) String> skills,
        @Size(max = 10) List<@Size(max = 16) String> yearsInCurrentRole,
        @Size(max = 10) List<@Size(max = 16) String> yearsOfExperience,
        @Size(max = 50, message = "Too many locations") List<@Size(max = 160) String> locations,
        @Min(value = 1, message = "The radius is at least a mile")
        @Max(value = 500, message = "The radius is at most 500 miles") Integer locationRadius,
        @Size(max = 50, message = "Too many companies") List<@Size(max = 160) String> companies,
        @Size(max = 50, message = "Too many company domains") List<@Size(max = 253) String> domains,
        @Size(max = 16) String companyMatch,
        @Size(max = 50, message = "Too many excluded companies") List<@Size(max = 160) String> excludedCompanies,
        @Size(max = 10) List<@Size(max = 16) String> companySizes,
        @Size(max = 50, message = "Too many industries") List<@Size(max = 160) String> industries,
        @Size(max = 50, message = "Too many excluded industries") List<@Size(max = 160) String> excludedIndustries,
        @Size(max = 20, message = "Too many languages") List<@Valid PeopleLanguageDto> languages,
        @Size(max = 50, message = "Too many schools or degrees") List<@Size(max = 500) String> education,
        @Size(max = 500) String keyword,
        @Size(max = 3) List<@Size(max = 16) String> contactTypes
) {

    public static PeopleFilterDto of(PeopleFilter filter) {
        return new PeopleFilterDto(filter.name(), filter.jobTitles(), filter.titleMatch(),
                filter.includeRelatedTitles(), filter.recentlyChangedJobs(), filter.excludedJobTitles(),
                filter.seniorities(), filter.jobFunctions(), filter.skills(), filter.yearsInCurrentRole(),
                filter.yearsOfExperience(), filter.locations(), filter.locationRadius(), filter.companies(),
                filter.domains(), filter.companyMatch(), filter.excludedCompanies(), filter.companySizes(),
                filter.industries(), filter.excludedIndustries(),
                filter.languages().stream().map(PeopleLanguageDto::of).toList(), filter.education(),
                filter.keyword(), filter.contactTypes());
    }
}
