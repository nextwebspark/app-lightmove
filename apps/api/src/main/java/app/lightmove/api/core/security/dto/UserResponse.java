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

        /** Null until signup step 2 — the frontend routes into the app or back into the wizard on it. */
        WorkspaceSummary workspace,

        /**
         * True when the user filled in the wizard but has not verified, so what they asked for is held
         * (see {@code PendingOnboarding}). The SPA routes on actual state, not a step counter, so a
         * closed tab does not lose the wizard.
         */
        boolean onboardingHeld,

        /**
         * The redeemable invitation addressed to this user, when they are not yet placed. Server-derived
         * so an invitee is routed to "join {workspace}" from any tab — the emailed token lives in one
         * tab's sessionStorage, but this survives everywhere the session does. Null once placed.
         */
        PendingInvitationSummary pendingInvitation
) {}
