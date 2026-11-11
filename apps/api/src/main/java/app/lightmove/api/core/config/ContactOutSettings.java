package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code lightmove.enrichment.contactout.*}, behind Find email/phone and ContactOut sourcing. A blank key offers
 * none of it and never fails the boot.
 */
public record ContactOutSettings(
        String apiKey,
        @DefaultValue("https://api.contactout.com") String baseUrl,

        /** Their published ceiling for this endpoint family is 150 a minute; paced well under. */
        @DefaultValue("2") int requestsPerSecond,

        /** People Search, Find executives' and Strategy's alike: their published ceiling is 60 a minute, so one a second. */
        @DefaultValue("1") int searchRequestsPerSecond,

        /** People Count is free and capped at 1,000 a minute; paced apart from search so typing never waits on a run. */
        @DefaultValue("10") int countRequestsPerSecond,

        /**
         * How many lookups one user may run a minute, per channel. The per-candidate guard stops a
         * row being billed twice; this stops one caller scripting the endpoint across a whole grid.
         */
        @DefaultValue("20") int lookupsPerUserPerMinute,

        /** Strategy people-search pages one user may buy a minute: 25 credits a page, so a scripted pager is capped. */
        @DefaultValue("10") int searchPagesPerUserPerMinute
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
