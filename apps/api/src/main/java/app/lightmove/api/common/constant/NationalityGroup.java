package app.lightmove.api.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * The nine groups an executive's nationality is recorded or counted under — the Gulf six by name, and
 * everyone else as Western expat, South Asian or Arab expat, non-GCC.
 *
 * <p>{@code Candidate.nationality} stays a free-text column for {@link NoticePeriod}'s reason: a
 * spreadsheet states whatever it states, and the picker offers an off-vocabulary value back "as
 * recorded" rather than clearing it. This enum exists so every caller that proposes or counts a
 * nationality — the Background section's picker, the report's grouping, an AI inference — reads the
 * same nine spellings from one place instead of three.
 */
@RequiredArgsConstructor
public enum NationalityGroup {

    SAUDI("Saudi", true),
    EMIRATI("Emirati", true),
    QATARI("Qatari", true),
    KUWAITI("Kuwaiti", true),
    OMANI("Omani", true),
    BAHRAINI("Bahraini", true),
    WESTERN_EXPAT("Western expat", false),
    SOUTH_ASIAN("South Asian", false),
    ARAB_EXPAT_NON_GCC("Arab expat, non-GCC", false);

    private final String label;
    private final boolean gcc;

    public String value() {
        return label;
    }

    public boolean isGcc() {
        return gcc;
    }

    /** The group whose stored spelling matches, case-insensitively, or null for anything else. */
    public static NationalityGroup ofLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        for (NationalityGroup group : values()) {
            if (group.label.equalsIgnoreCase(trimmed)) {
                return group;
            }
        }
        return null;
    }
}
