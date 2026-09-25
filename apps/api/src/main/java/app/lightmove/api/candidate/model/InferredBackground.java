package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.Gender;

/** What the model read off a researched profile — any of the three may be null. */
public record InferredBackground(String nationality, Gender gender, Integer yearsExperience) {

    public static final InferredBackground NONE = new InferredBackground(null, null, null);

    public boolean isEmpty() {
        return nationality == null && gender == null && yearsExperience == null;
    }
}
