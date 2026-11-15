package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.config.LegalSettings;

/** The data processing agreement a new workspace's founder is asked to accept; both null when none is asked. */
public record WorkspaceLegalResponse(String dpaVersion, String dpaUrl) {

    public static WorkspaceLegalResponse from(LegalSettings legal) {
        return new WorkspaceLegalResponse(legal.dpaVersion(), legal.dpaUrl());
    }
}
