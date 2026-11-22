package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.dto.StartPageResponse;
import app.lightmove.api.workspace.model.WorkspaceMember;

/**
 * The member's start page for the auth response. Declared here and answered by {@code startpage}, which reads the
 * dashboard and project features, so that core never depends on them.
 */
public interface StartPageLookup {

    /** @param membership the session's active membership, its roles already read */
    StartPageResponse startPageOf(WorkspaceMember membership);
}
