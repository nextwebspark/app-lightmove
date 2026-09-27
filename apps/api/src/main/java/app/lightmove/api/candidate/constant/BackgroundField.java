package app.lightmove.api.candidate.constant;

/** The fields a model may propose on a researched executive; the keys of {@code Candidate.aiInferredFields}. */
public enum BackgroundField {

    NATIONALITY("nationality"),
    GENDER("gender"),
    YEARS_EXPERIENCE("yearsExperience"),
    SENIORITY("seniority");

    private final String key;

    BackgroundField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
