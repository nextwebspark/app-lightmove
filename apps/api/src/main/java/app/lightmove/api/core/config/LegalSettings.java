package app.lightmove.api.core.config;

import org.springframework.util.StringUtils;

/**
 * Uncava's own legal documents — {@code lightmove.legal.*}. The data processing agreement a founder accepts for their
 * firm (#928), and Uncava's public notice to the executives it holds data on (#1490, decisions B2 and B6), linked
 * from every email a candidate receives. Each pair is blank until counsel publishes it: then nothing is asked or
 * linked, and nothing is recorded.
 */
public record LegalSettings(String dpaVersion, String dpaUrl, String candidateNoticeVersion,
                            String candidateNoticeUrl) {

    public LegalSettings {
        if (StringUtils.hasText(dpaVersion) != StringUtils.hasText(dpaUrl)) {
            throw new IllegalArgumentException(
                    "lightmove.legal.dpa-version and lightmove.legal.dpa-url are set together, or neither");
        }
        if (StringUtils.hasText(candidateNoticeVersion) != StringUtils.hasText(candidateNoticeUrl)) {
            throw new IllegalArgumentException("lightmove.legal.candidate-notice-version and "
                    + "lightmove.legal.candidate-notice-url are set together, or neither");
        }
        if (StringUtils.hasText(candidateNoticeUrl) && !candidateNoticeUrl.trim().startsWith("https://")) {
            throw new IllegalArgumentException("lightmove.legal.candidate-notice-url must be an https link");
        }
        dpaVersion = textOrNull(dpaVersion);
        dpaUrl = textOrNull(dpaUrl);
        candidateNoticeVersion = textOrNull(candidateNoticeVersion);
        candidateNoticeUrl = textOrNull(candidateNoticeUrl);
    }

    public boolean asksForDpa() {
        return dpaVersion != null;
    }

    public boolean linksCandidateNotice() {
        return candidateNoticeUrl != null;
    }

    public static LegalSettings none() {
        return new LegalSettings(null, null, null, null);
    }

    private static String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
