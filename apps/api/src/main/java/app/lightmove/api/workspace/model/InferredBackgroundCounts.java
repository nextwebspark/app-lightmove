package app.lightmove.api.workspace.model;

import org.springframework.modulith.NamedInterface;

/** People in a workspace holding an AI-suggested gender, and an AI-suggested nationality or reading. */
@NamedInterface(name = "api", propagate = false)
public record InferredBackgroundCounts(long gender, long nationality) {

    public static final InferredBackgroundCounts NONE = new InferredBackgroundCounts(0, 0);
}
