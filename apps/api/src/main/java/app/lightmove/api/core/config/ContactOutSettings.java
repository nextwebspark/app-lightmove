package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The ContactOut account behind the candidate drawer's Find email / Find phone buttons —
 * {@code lightmove.enrichment.contactout.*}.
 *
 * <p>A blank key means the buttons are not offered at all, so a fresh clone runs with no ContactOut
 * account. Unlike {@code lightmove.enrichment.provider}, there is no provider name to contradict here:
 * an empty key is a deployment without an account, not a misconfigured one, so it never fails the boot.
 */
public record ContactOutSettings(
        String apiKey,
        @DefaultValue("https://api.contactout.com") String baseUrl,

        /** Their published ceiling for this endpoint family is 150 a minute; paced well under. */
        @DefaultValue("2") int requestsPerSecond,

        /**
         * How many lookups one user may run a minute, per channel. The per-candidate guard stops a
         * row being billed twice; this stops one caller scripting the endpoint across a whole grid.
         */
        @DefaultValue("20") int lookupsPerUserPerMinute
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
