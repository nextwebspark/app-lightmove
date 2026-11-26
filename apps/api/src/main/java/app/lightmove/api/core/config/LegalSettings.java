package app.lightmove.api.core.config;

import org.springframework.util.StringUtils;

/**
 * Uncava's own legal documents — {@code lightmove.legal.*}: the DPA a founder accepts (#928) and the notice to
 * executives every candidate email links (#1490). While a pair is blank, nothing asks for or links it.
 */
public record LegalSettings(String dpaVersion, String dpaUrl, String candidateNoticeVersion,
                            String candidateNoticeUrl) {

    /** {@code app_lm_person.uncava_notice_version} is {@code varchar(32)} (V247). */
    public static final int CANDIDATE_NOTICE_VERSION_MAX = 32;

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
        if (StringUtils.hasText(candidateNoticeVersion)
                && candidateNoticeVersion.trim().length() > CANDIDATE_NOTICE_VERSION_MAX) {
            throw new IllegalArgumentException("lightmove.legal.candidate-notice-version must be at most "
                    + CANDIDATE_NOTICE_VERSION_MAX + " characters");
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
