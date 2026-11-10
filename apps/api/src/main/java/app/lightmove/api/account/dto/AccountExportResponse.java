package app.lightmove.api.account.dto;

import app.lightmove.api.core.security.dto.ActiveSessionResponse;
import app.lightmove.api.core.security.dto.MfaStatusResponse;
import java.time.Instant;
import java.util.List;

/**
 * The person's account as a file (#926): what Uncava keeps about them as a user. What they added inside a workspace —
 * notes, candidates, positions — is that workspace's, and its admin exports it.
 */
public record AccountExportResponse(
        Instant exportedAt,
        ExportedAccount account,
        ExportedConsents consents,
        boolean hasPassword,
        List<ExportedSignInMethod> signInProviders,
        MfaStatusResponse twoFactor,
        List<ExportedMembership> workspaces,
        List<ActiveSessionResponse> sessions,
        /** The account's own sign-in events of the last year, newest first. */
        List<ExportedSignIn> signIns
) {}
