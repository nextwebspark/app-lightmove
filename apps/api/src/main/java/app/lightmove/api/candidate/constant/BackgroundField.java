package app.lightmove.api.candidate.constant;

/**
 * The three fields an AI inference can propose on a captured executive — nationality, gender and
 * years of experience — and a researcher's own edit can then confirm.
 *
 * <p>{@code Candidate.aiInferredFields} stores a set of these keys: whichever of the three currently
 * hold a value nobody has reviewed since {@code enrich} filled it in. One enum rather than three
 * strings typed out in both {@code enrich} and {@code describe}, so the two can never drift onto
 * different spellings of the same fact.
 */
public enum BackgroundField {

    NATIONALITY("nationality"),
    GENDER("gender"),
    YEARS_EXPERIENCE("yearsExperience");

    private final String key;

    BackgroundField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
