package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.WorkspaceAiControls;

/** What the AI may do with the workspace's people; every staff member reads it, so the drawer hides what is off. */
public record WorkspaceAiControlsResponse(
        boolean inferGender,
        boolean inferNationality,
        boolean draftAssessments,
        boolean enrichAutomatically
) {

    public static WorkspaceAiControlsResponse of(WorkspaceAiControls controls) {
        return new WorkspaceAiControlsResponse(controls.inferGender(), controls.inferNationality(),
                controls.draftAssessments(), controls.enrichAutomatically());
    }
}
