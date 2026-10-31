package app.lightmove.api.workspace.dto;

import java.util.UUID;

/** Someone the audit log's Person filter offers: everyone the workspace ever held, removed members included. */
public record AuditLogPersonResponse(UUID userId, String name, String avatarUrl, boolean removed) {}
