package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.workspace.dto.WorkspaceSummary;
import java.util.List;
import java.util.UUID;

/** The current user, as {@code /auth/me} and every auth response return them. */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String title,
        String avatarUrl,
        boolean emailVerified,

        /**
         * Whether this account has a local password at all. False for a provider-only sign-in, which is
         * what lets Settings → Security offer the reset flow instead of a current-password box that can
         * never be filled. Disclosed only to the account's owner.
         */
        boolean hasPassword,

        /** IANA zone id — the app formats dates in it. Defaults to Asia/Dubai until the user says otherwise. */
        String timezone,

        /** The language tag the user picked in Settings → Profile. Stored ahead of the app being translated. */
        String locale,

        /** The session's workspace — the token's {@code wsId} — or null; the frontend routes on it. */
        WorkspaceSummary workspace,

        /** Every active membership, oldest first — what the switcher lists. */
        List<WorkspaceSummary> workspaces,

        /**
         * Redeemable invitations to workspaces the user is not yet in. Server-derived so an invitee is
         * routed to "join {workspace}" from any tab, not only the one holding the emailed token.
         */
        List<PendingInvitationSummary> pendingInvitations,

        /** What the user may do outside any workspace — empty for everyone but LightMove staff. */
        List<PlatformAction> platformActions
) {}
