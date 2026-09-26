package app.lightmove.api.candidate.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The three fields a model may propose on a researched executive; the keys of {@code Candidate.aiInferredFields}. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum BackgroundField {

    NATIONALITY("nationality"),
    GENDER("gender"),
    YEARS_EXPERIENCE("yearsExperience");

    private final String key;
}
