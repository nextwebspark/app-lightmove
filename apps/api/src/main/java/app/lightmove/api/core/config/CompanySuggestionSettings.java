package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** The adjacent-sector and inferred-tag suggestions — {@code CompanyQueryService.suggestionsFor}. */
public record CompanySuggestionSettings(
        /** How many adjacent sectors the suggestion panel shows at most. */
        @DefaultValue("10") int adjacentSectorLimit,

        /** How many inferred tags survive to the response. */
        @DefaultValue("8") int inferredTagLimit,

        /** How many co-occurring tags to pull before filtering out ground the sectors already cover. */
        @DefaultValue("30") int inferredTagFetchSize
) {}
