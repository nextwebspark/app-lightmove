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
         * shortcut but an accident. Past the cap the request adds this many and says so, rather than
         * failing or pretending it took everything.
         */
        @DefaultValue("500") int bulkAddLimit
) {}
