package app.lightmove.api.dataexport.model;

import java.util.UUID;

/** An export row was committed; its worker builds the zip off the request thread. */
public record WorkspaceExportRequested(UUID exportId, UUID workspaceId) {
}
