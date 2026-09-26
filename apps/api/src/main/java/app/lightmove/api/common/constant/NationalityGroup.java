package app.lightmove.api.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * The nine groups a nationality is recorded or counted under — the Gulf six by name, then Western
 * expat, South Asian and Arab expat, non-GCC. {@code Candidate.nationality} stays free text, as
 * {@link NoticePeriod} explains; this is the one vocabulary every caller reads.
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
