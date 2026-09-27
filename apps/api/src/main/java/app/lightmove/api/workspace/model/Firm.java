package app.lightmove.api.workspace.model;

import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.workspace.constant.WorkspaceMode;

/** The firm a workspace is: who it hires for, and its own profile as the assistant is told it. */
public record Firm(WorkspaceMode mode, HiringCompanyProfile profile) {
}
