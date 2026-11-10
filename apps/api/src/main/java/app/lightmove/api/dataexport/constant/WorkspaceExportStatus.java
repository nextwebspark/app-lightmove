package app.lightmove.api.dataexport.constant;

/** Where a whole-workspace export stands; only {@code READY} has a file to download. */
public enum WorkspaceExportStatus {
    RUNNING,
    READY,
    FAILED,
    EXPIRED
}
