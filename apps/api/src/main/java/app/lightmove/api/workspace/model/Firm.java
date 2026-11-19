package app.lightmove.api.workspace.model;

import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.core.tenant.WorkspaceMode;
import org.springframework.modulith.NamedInterface;

/** The firm a workspace is: who it hires for, and its own profile as the assistant is told it. */
@NamedInterface(name = "api", propagate = false)
public record Firm(WorkspaceMode mode, HiringCompanyProfile profile) {
}
