package app.lightmove.api.workspace.dto;

/** The data processing agreement a new workspace's founder is asked to accept; both null when none is asked. */
public record WorkspaceLegalResponse(String dpaVersion, String dpaUrl) {}
