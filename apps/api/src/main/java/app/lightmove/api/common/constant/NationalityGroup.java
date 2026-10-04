package app.lightmove.api.common.constant;

import lombok.RequiredArgsConstructor;

/**
 * The eleven groups a nationality is recorded or counted under — the Gulf six by name, then Western
 * expat, South Asian, Asian, Arab expat, non-GCC and Other expat. {@code Candidate.nationality} stays
 * free text, as {@link NoticePeriod} explains; this is the one vocabulary every caller reads.
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
    ASIAN("Asian", false),
    ARAB_EXPAT_NON_GCC("Arab expat, non-GCC", false),
    OTHER_EXPAT("Other expat", false);

    /** The nationality prompt's rubric word for South Asian — the stored label stays "South Asian". */
    private static final String SUBCONTINENT = "Subcontinent";

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
        if (SUBCONTINENT.equalsIgnoreCase(trimmed)) {
            return SOUTH_ASIAN;
        }
        for (NationalityGroup group : values()) {
            if (group.label.equalsIgnoreCase(trimmed)) {
                return group;
            }
        }
        return null;
    }
}
