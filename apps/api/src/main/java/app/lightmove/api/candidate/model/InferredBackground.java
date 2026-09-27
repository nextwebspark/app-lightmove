package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.Seniority;

/** What the model read off a researched profile — any of them may be null. */
public record InferredBackground(String nationality, Gender gender, Integer yearsExperience, Seniority seniority) {

    public static final InferredBackground NONE = new InferredBackground(null, null, null, null);
}
