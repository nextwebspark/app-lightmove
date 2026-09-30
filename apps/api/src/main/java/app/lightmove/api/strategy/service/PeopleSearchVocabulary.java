package app.lightmove.api.strategy.service;

import app.lightmove.api.common.service.ClasspathJsonLoader;
import app.lightmove.api.strategy.dto.PeopleFacetsResponse;
import app.lightmove.api.strategy.dto.VocabularyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ContactOut's accepted values for People Search, from {@code data/contactout-people-vocabulary.json} —
 * a verbatim copy of the sheet its API reference links, which is the authority over the reference's own
 * prose examples ({@code "vice president"}, {@code "Computer Software"} are older spellings it no longer
 * lists). A token outside it would narrow the count to nothing, so a save carrying one is refused.
 */
@Component
public class PeopleSearchVocabulary {

    private static final String RESOURCE = "data/contactout-people-vocabulary.json";

    private final PeopleFacetsResponse facets;
    private final Set<String> seniorities;
    private final Set<String> jobFunctions;
    private final Set<String> companySizes;
    private final Set<String> yearsOfExperience;
    private final Set<String> yearsInCurrentRole;
    private final Set<String> languageProficiencies;
    private final Set<String> industries;

    public PeopleSearchVocabulary(ObjectMapper json) {
        this.facets = ClasspathJsonLoader.load(json, RESOURCE, new TypeReference<PeopleFacetsResponse>() {});
        this.seniorities = valuesOf(facets.seniorities());
        this.jobFunctions = valuesOf(facets.jobFunctions());
        this.companySizes = valuesOf(facets.companySizes());
        this.yearsOfExperience = valuesOf(facets.yearsOfExperience());
        this.yearsInCurrentRole = valuesOf(facets.yearsInCurrentRole());
        this.languageProficiencies = valuesOf(facets.languageProficiencies());
        this.industries = Set.copyOf(facets.industries());
    }

    public PeopleFacetsResponse facets() {
        return facets;
    }

    public boolean isSeniority(String token) {
        return seniorities.contains(token);
    }

    public boolean isJobFunction(String token) {
        return jobFunctions.contains(token);
    }

    public boolean isCompanySize(String token) {
        return companySizes.contains(token);
    }

    public boolean isYearsOfExperience(String token) {
        return yearsOfExperience.contains(token);
    }

    public boolean isYearsInCurrentRole(String token) {
        return yearsInCurrentRole.contains(token);
    }

    public boolean isLanguageProficiency(String token) {
        return languageProficiencies.contains(token);
    }

    public boolean isIndustry(String token) {
        return industries.contains(token);
    }

    private static Set<String> valuesOf(List<VocabularyOption> options) {
        return options.stream().map(VocabularyOption::value).collect(Collectors.toUnmodifiableSet());
    }
}
