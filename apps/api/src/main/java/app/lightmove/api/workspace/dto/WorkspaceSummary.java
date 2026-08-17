package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The workspace as the auth response and the shell render it. */
public record WorkspaceSummary(
        UUID id,
        String name,
        String slug,
        String logoMark,

        /**
         * Null for a pure client. It is an internal signal — what the firm's own colleagues'
         * addresses are expected to look like — and a hiring-company contact has no use for it.
         * The name, slug and mark stay: that is the brand they are dealing with, and the portal
         * renders it.
         */
        String emailDomain,

        /** The caller's workspace roles — a set, sorted for stable rendering. */
        List<WorkspaceRole> roles,

        /** When this membership became active. Settings → Profile reads it as "joined Mar 2026". */
        Instant joinedAt
) {}
