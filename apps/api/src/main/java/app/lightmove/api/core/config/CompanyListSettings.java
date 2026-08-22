package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** The Strategy screen's paged company list, and the bulk add that runs off the same filter. */
public record CompanyListSettings(
        /** Rows per page when the request names no explicit {@code size}. */
        @DefaultValue("25") int defaultPageSize,

        /** Hard ceiling on one page; a larger requested {@code size} is rejected — a scope, not an attack. */
        @DefaultValue("100") int maxPageSize,

        /**
         * How many companies one "Add all to Universe" may take. An untouched filter matches the whole
         * universe — 71,822 rows — and a button that quietly writes that many into a mandate is not a
         * shortcut but an accident. A filter matching more than this is <b>refused</b>, not truncated:
         * every row here is one a consultant then triages by hand, and taking "the first 200" would
         * silently decide which 200 the mandate got.
         */
        @DefaultValue("200") int bulkAddLimit
) {

    /**
     * The ceiling on {@link #bulkAddLimit}, enforced at startup rather than discovered in production.
     *
     * <p>{@code TriageCompanyWriter} binds nine parameters per row in one statement, and Postgres caps
     * a statement at 65,535 of them — a hard failure somewhere above 7,200 rows. This sits far below
     * that, because the product answer to "let me add more" is a narrower filter rather than a bigger
     * batch.
     */
    public static final int MAX_BULK_ADD_LIMIT = 1_000;

    public CompanyListSettings {
        if (bulkAddLimit < 1 || bulkAddLimit > MAX_BULK_ADD_LIMIT) {
            throw new IllegalArgumentException(
                    "lightmove.company.list.bulk-add-limit must be between 1 and " + MAX_BULK_ADD_LIMIT
                            + ", but was " + bulkAddLimit);
        }
    }
}
