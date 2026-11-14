package app.lightmove.api.core.config;

import org.springframework.util.StringUtils;

/**
 * The data processing agreement a founder accepts for their firm (#928) — {@code lightmove.legal.*}. Blank until
 * counsel publishes one: then nothing is asked, and nothing is recorded.
 */
public record LegalSettings(String dpaVersion, String dpaUrl) {

    public LegalSettings {
        if (StringUtils.hasText(dpaVersion) != StringUtils.hasText(dpaUrl)) {
            throw new IllegalArgumentException(
                    "lightmove.legal.dpa-version and lightmove.legal.dpa-url are set together, or neither");
        }
        dpaVersion = StringUtils.hasText(dpaVersion) ? dpaVersion.trim() : null;
        dpaUrl = StringUtils.hasText(dpaUrl) ? dpaUrl.trim() : null;
    }

    public boolean asksForDpa() {
        return dpaVersion != null;
    }

    public static LegalSettings none() {
        return new LegalSettings(null, null);
    }
}
