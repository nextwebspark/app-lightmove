package app.lightmove.api.strategy.service;

import app.lightmove.api.core.config.CompanySearchSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.strategy.dto.CompanySuggestion;
import java.util.List;
import org.springframework.stereotype.Service;

/** The name typeahead behind both {@code /companies/search} and {@code /onboarding/companies}. */
@Service
public class CompanySuggestionSearch {

    private final ApolloCompanyQueryService companies;
    private final CompanySearchSettings settings;

    public CompanySuggestionSearch(ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.settings = properties.company().search();
    }

    /** A query shorter than {@code minQueryLength} answers nothing rather than the head of the universe. */
    public List<CompanySuggestion> suggest(String rawQuery, Integer limit, int minQueryLength) {
        String query = acceptedQuery(rawQuery);
        if (query.isEmpty() || query.length() < minQueryLength) {
            return List.of();
        }
        return companies.typeahead(query, resolvedLimit(limit)).stream()
                .map(CompanySuggestion::of)
                .toList();
    }

    public String acceptedQuery(String rawQuery) {
        String trimmed = rawQuery.trim();
        if (trimmed.length() > settings.maxQueryLength()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "q exceeds " + settings.maxQueryLength() + " characters");
        }
        return trimmed;
    }

    /** Refused rather than clamped: a silently narrowed limit is a wrong answer. */
    private int resolvedLimit(Integer limit) {
        if (limit == null) {
            return settings.defaultResultLimit();
        }
        if (limit < 1 || limit > settings.maxResultLimit()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "limit must be between 1 and " + settings.maxResultLimit());
        }
        return limit;
    }
}
