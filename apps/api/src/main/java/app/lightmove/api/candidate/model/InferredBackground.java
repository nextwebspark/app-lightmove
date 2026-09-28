package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.Seniority;

/**
 * What the assessment call read off a researched profile — any of the three may be null. Nationality
 * is not among them: it has its own classifier and arrives as a {@link NationalityReading}.
 */
public record InferredBackground(Gender gender, Integer yearsExperience, Seniority seniority) {

    public static final InferredBackground NONE = new InferredBackground(null, null, null);
}
