package app.lightmove.api.candidate.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The four fields a model may propose on a researched executive; the keys of {@code Person.aiInferredFields}. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum BackgroundField {

    NATIONALITY("nationality"),
    GENDER("gender"),
    YEARS_EXPERIENCE("yearsExperience"),
    SENIORITY("seniority");

    private final String key;
}
