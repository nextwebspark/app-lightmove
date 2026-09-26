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

        /** Null for a pure client: an internal signal a hiring-company contact has no use for. */
        String emailDomain,

        /** Sorted for stable rendering. */
        List<WorkspaceRole> roles,

        /** When this membership became active. */
        Instant joinedAt,

        /** The universe company the firm was picked as at signup; null for one typed in by hand. */
        WorkspaceCompanyResponse company,

        /** Signup's description of the firm, so going back to that step shows what was saved. */
        String companySize,
        String primaryRegion,
        String teamFocus
) {}
