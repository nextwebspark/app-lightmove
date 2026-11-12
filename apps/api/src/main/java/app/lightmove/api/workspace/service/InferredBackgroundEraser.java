package app.lightmove.api.workspace.service;

import app.lightmove.api.workspace.model.InferredBackgroundCounts;
import java.util.UUID;

/**
 * The AI's unconfirmed gender and nationality across a workspace's people, implemented in {@code candidate} so
 * {@code workspace} never reads back into it. A value a person typed, imported or confirmed is never counted or cleared.
 */
public interface InferredBackgroundEraser {

    /** What turning the two inferences off would clear now. */
    InferredBackgroundCounts heldIn(UUID workspaceId);

    /** Clears what each named inference left, in the caller's transaction, and answers how many people it touched. */
    InferredBackgroundCounts clear(UUID workspaceId, boolean gender, boolean nationality);
}
