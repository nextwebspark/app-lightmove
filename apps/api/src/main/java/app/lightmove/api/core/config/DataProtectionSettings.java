package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Whether the people controls of epic #917 are offered; off, their controllers are never made. */
public record DataProtectionSettings(
        @DefaultValue("false") boolean enabled,
        /** Privacy notices one workspace may send in a day (#923): a brake on Uncava's sender, not on the firm. */
        @DefaultValue("500") int privacyNoticesPerDay,
        /** When the daily consent scan runs (#1267): lapsed requests and consents close, renewals go. */
        @DefaultValue("0 20 3 * * *") String consentScan) {

    public static final String SWITCH = "lightmove.data-protection.enabled";
}
