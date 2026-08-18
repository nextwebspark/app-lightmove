package app.lightmove.api.core.security.dto;

import app.lightmove.api.workspace.dto.WorkspaceSummary;
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

        /** Null until the organisation step — the frontend routes into the app or back into the wizard on it. */
        WorkspaceSummary workspace,

        /**
         * The redeemable invitation addressed to this user, when they are not yet placed. Server-derived
         * so an invitee is routed to "join {workspace}" from any tab — the emailed token lives in one
         * tab's sessionStorage, but this survives everywhere the session does. Null once placed.
         */
        PendingInvitationSummary pendingInvitation
) {}
