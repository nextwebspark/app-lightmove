package app.lightmove.api.workspace.model;

/**
 * What the workspace lets the AI do with the people it holds (#922): infer a gender, infer a nationality, draft an
 * assessment, and run by itself when a person is captured or sourced. Each is the workspace's choice, never a member's.
 */
public record WorkspaceAiControls(
        boolean inferGender,
        boolean inferNationality,
        boolean draftAssessments,
        boolean enrichAutomatically
) {

    /** Every switch on: what a workspace that existed before V211 holds. */
    public static WorkspaceAiControls allOn() {
        return new WorkspaceAiControls(true, true, true, true);
    }
}
