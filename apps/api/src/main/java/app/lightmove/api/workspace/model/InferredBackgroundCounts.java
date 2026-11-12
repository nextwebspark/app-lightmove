package app.lightmove.api.workspace.model;

/** People in a workspace holding an AI-suggested gender, and an AI-suggested nationality or reading. */
public record InferredBackgroundCounts(long gender, long nationality) {

    public static final InferredBackgroundCounts NONE = new InferredBackgroundCounts(0, 0);
}
