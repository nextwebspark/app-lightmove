package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.InferredBackgroundCounts;

/** How many people turning each inference off would clear, for the confirmation before it does. */
public record InferredBackgroundResponse(long genderValues, long nationalityValues) {

    public static InferredBackgroundResponse of(InferredBackgroundCounts counts) {
        return new InferredBackgroundResponse(counts.gender(), counts.nationality());
    }
}
