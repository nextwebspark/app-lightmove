package app.lightmove.api.workspace.service;

import app.lightmove.api.workspace.model.WorkspaceCompany;

/** The name a workspace is filed under and the universe company behind it, null for a typed name. */
record WorkspaceIdentity(String name, WorkspaceCompany company) {
}
