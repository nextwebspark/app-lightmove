package app.lightmove.api.strategy.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.strategy.constant.CompanyMatch;
import app.lightmove.api.strategy.constant.ContactDataType;
import app.lightmove.api.strategy.constant.TitleMatch;
import app.lightmove.api.strategy.dto.PeopleFilterDto;
import app.lightmove.api.strategy.dto.PeopleLanguageDto;
import app.lightmove.api.strategy.model.PeopleFilter;
import app.lightmove.api.strategy.model.PeopleLanguage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The People sidebar's selection read into a filter ContactOut will accept as sent. Free text is trimmed
 * and de-duplicated; a vocabulary token must be one the accepted-values sheet lists, and the one pair of
 * filters the API refuses together is refused here, so the vendor's 400 never reaches a researcher.
 */
@Component
@RequiredArgsConstructor
public class PeopleFilterReader {

    private final PeopleSearchVocabulary vocabulary;

    public PeopleFilter read(PeopleFilterDto dto) {
        List<String> yearsInCurrentRole = tokens(dto.yearsInCurrentRole(), vocabulary::isYearsInCurrentRole,
                "years in current role");
        if (Boolean.TRUE.equals(dto.recentlyChangedJobs()) && !yearsInCurrentRole.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Recently changed jobs cannot be combined with years in current role.");
        }
        List<String> locations = texts(dto.locations());
        Integer radius = locations.size() == 1 ? dto.locationRadius() : null;
        return new PeopleFilter(text(dto.name()), texts(dto.jobTitles()), matchOf(dto.titleMatch()),
                dto.includeRelatedTitles(), dto.recentlyChangedJobs(), texts(dto.excludedJobTitles()),
                tokens(dto.seniorities(), vocabulary::isSeniority, "seniority"),
                tokens(dto.jobFunctions(), vocabulary::isJobFunction, "job function"),
                texts(dto.skills()), yearsInCurrentRole,
                tokens(dto.yearsOfExperience(), vocabulary::isYearsOfExperience, "years of experience"),
                locations, radius, texts(dto.companies()), texts(dto.domains()),
                companyMatchOf(dto.companyMatch()), texts(dto.excludedCompanies()),
                tokens(dto.companySizes(), vocabulary::isCompanySize, "company size"),
                tokens(dto.industries(), vocabulary::isIndustry, "industry"),
                tokens(dto.excludedIndustries(), vocabulary::isIndustry, "industry"),
                languagesOf(dto.languages()), texts(dto.education()), text(dto.keyword()),
                tokens(dto.contactTypes(), token -> ContactDataType.fromValue(token) != null, "contact type"));
    }

    private static String matchOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (TitleMatch.fromValue(token) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown title match: " + token);
        }
        return token;
    }

    private static String companyMatchOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (CompanyMatch.fromValue(token) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown company match: " + token);
        }
        return token;
    }

    private List<PeopleLanguage> languagesOf(List<PeopleLanguageDto> languages) {
        if (languages == null) {
            return List.of();
        }
        return languages.stream()
                .filter(Objects::nonNull)
                .map(language -> new PeopleLanguage(language.language().strip(),
                        tokens(language.proficiencies(), vocabulary::isLanguageProficiency, "language proficiency")))
                .toList();
    }

    private static List<String> tokens(List<String> values, Predicate<String> isKnown, String label) {
        List<String> tokens = texts(values);
        for (String token : tokens) {
            if (!isKnown.test(token)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown " + label + ": " + token);
            }
        }
        return tokens;
    }

    private static List<String> texts(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values.stream()
                .map(PeopleFilterReader::text)
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
