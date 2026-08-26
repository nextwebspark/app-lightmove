package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** The company and keyword typeaheads over the universe. */
public record CompanySearchSettings(
        /** Rows returned when the request names no explicit {@code limit}. */
        @DefaultValue("10") int defaultResultLimit,

        /** Hard ceiling on rows one search returns; a larger requested {@code limit} clamps to this. */
        @DefaultValue("25") int maxResultLimit,

        /** Longest accepted query text; beyond it the request is rejected — a scope, not an attack. */
        @DefaultValue("100") int maxQueryLength,

        /** Rows returned by the keyword typeahead when the request names no explicit {@code limit}. */
        @DefaultValue("20") int keywordSuggestionLimit,

        /**
         * Shortest keyword query answered at all. The company picker's rule — too short a query
         * returns nothing rather than the head of the universe — applied to a vocabulary of 765,169
         * keywords, where one letter matches tens of thousands of them.
         */
        @DefaultValue("2") int keywordMinQueryLength,

        /**
         * How many companies a keyword must reach before it is offered. Three in four are carried by
         * a single company — "slot demo gacor", "perfume mixology" — which is that company's own
         * marketing copy rather than an axis of the market. At ten, 27,347 of 765,169 remain.
         */
        @DefaultValue("10") int keywordMinCompanies
) {}
