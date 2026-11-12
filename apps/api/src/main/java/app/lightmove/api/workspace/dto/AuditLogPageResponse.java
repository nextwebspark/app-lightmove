package app.lightmove.api.workspace.dto;

import java.util.List;

/** A page of the audit log, newest first; {@code nextCursor} goes back as {@code before}, null on the last page. */
public record AuditLogPageResponse(List<AuditLogEntryResponse> entries, Long nextCursor) {}
