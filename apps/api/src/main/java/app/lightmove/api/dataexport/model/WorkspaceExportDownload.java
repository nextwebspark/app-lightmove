package app.lightmove.api.dataexport.model;

/** A ready export's file, for the controller to stream. */
public record WorkspaceExportDownload(String fileName, long sizeBytes, String storageKey) {
}
