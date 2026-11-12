package app.lightmove.api.workspace.dto;

import app.lightmove.api.workspace.model.WorkspaceAiControls;
import jakarta.validation.constraints.NotNull;

/** Settings → Data & retention's AI on people: all four switches every time, so no caller turns one off by omission. */
public record UpdateWorkspaceAiControlsRequest(
        @NotNull(message = "Say whether the AI infers gender")
        Boolean inferGender,

        @NotNull(message = "Say whether the AI infers nationality")
        Boolean inferNationality,

        @NotNull(message = "Say whether the AI drafts assessments")
        Boolean draftAssessments,

        @NotNull(message = "Say whether the AI runs on capture")
        Boolean enrichAutomatically
) {

    public WorkspaceAiControls controls() {
        return new WorkspaceAiControls(inferGender, inferNationality, draftAssessments, enrichAutomatically);
    }
}
