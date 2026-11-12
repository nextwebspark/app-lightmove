package app.lightmove.api.workspace.service;

import app.lightmove.api.workspace.model.WorkspaceAiControls;
import java.util.UUID;

/** What the workspace lets the AI do with its people (#922), for the features that run the AI and file its answers. */
public interface WorkspaceAiControlsReader {

    /** The switches as committed, read before a model call decides what to ask. */
    WorkspaceAiControls aiControlsOf(UUID workspaceId);

    /**
     * The switches for a write of what the AI inferred, inside the caller's transaction: the workspace row is held
     * {@code FOR SHARE} until it ends, and a change takes it {@code FOR UPDATE} before clearing, so a model answer
     * landing after the switch went off is never written back over the clear.
     */
    WorkspaceAiControls aiControlsHeldForWrite(UUID workspaceId);
}
