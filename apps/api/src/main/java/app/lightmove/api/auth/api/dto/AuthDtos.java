package app.lightmove.api.auth.api.dto;

import app.lightmove.api.auth.application.PasswordPolicy;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The HTTP contract for auth.
 *
 * <p>Grouped in one file on purpose: these are eight small records that are only meaningful together,
 * and eight one-record files would be filing, not structure.
 *
 * <p>The validation messages here are what the user reads under the field, so they are written as
 * instructions ("Use at least 8 characters") rather than as complaints ("password too short"). They
 * mirror the frontend's Zod schema — the client validates for a fast response, the server validates
 * because the client can be bypassed with one curl command.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Signup step 1. */
    public record SignupRequest(
            @NotBlank(message = "Enter your full name")
            @Size(max = 160, message = "That name is too long")
            String fullName,

            @NotBlank(message = "Enter your work email")
            @Email(message = "That doesn't look like a valid email")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Choose a password")
            @Size(min = PasswordPolicy.MIN_LENGTH, message = "Use at least 8 characters")
            @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
            String password,

            @AssertTrue(message = "You must accept the terms to continue")
            boolean termsAccepted
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Enter your email")
            @Email(message = "That doesn't look like a valid email")
            String email,

            @NotBlank(message = "Enter your password")
            String password
    ) {}

    public record ResendVerificationRequest(
            @NotBlank @Email String email
    ) {}

    /**
     * What a successful authentication returns.
     *
     * <p>The refresh token is <b>deliberately absent</b>. It leaves in an httpOnly cookie the browser
     * will not let script read — putting it in this body would defeat the entire point of the cookie.
     *
     * @param expiresIn seconds, so the SPA can schedule a refresh before the token dies rather than
     *                  discovering it has by taking a 401.
     */
    public record AuthResponse(
            String accessToken,
            long expiresIn,
            UserResponse user
    ) {}

    /** The current user, as {@code /auth/me} and every auth response return them. */
    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            String title,
            String avatarUrl,
            boolean emailVerified,

            /**
             * Null until signup step 2. The frontend reads this to decide whether to route into the
             * app or back into the onboarding wizard.
             */
            WorkspaceSummary workspace,

            /**
             * True when the user filled in the wizard but has not verified their address, so what they
             * asked for is held rather than done (see {@code PendingOnboarding}).
             *
             * <p>The SPA routes on the user's actual state rather than on a step counter, precisely so a
             * closed tab does not lose the wizard. Without this it cannot distinguish "has not started
             * onboarding" from "has finished it and is waiting on their inbox", and would drop the second
             * user back on an empty form they have already filled in.
             */
            boolean onboardingHeld
    ) {}

    public record WorkspaceSummary(
            UUID id,
            String name,
            String slug,
            String logoMark,
            String emailDomain,
            WorkspaceRole role
    ) {}

    /** Signup step 2. */
    public record CreateWorkspaceRequest(
            @NotBlank(message = "Enter your organization's name")
            @Size(max = 160, message = "That name is too long")
            String name,

            String companySize,
            String primaryRegion,

            /** The mockup's "Your role" — a job title, not an authority. See CreateWorkspaceCommand. */
            String jobTitle,
            String teamFocus
    ) {}

    /** One row of signup step 3. */
    public record InviteRequest(
            @NotBlank(message = "Enter an email address")
            @Email(message = "That doesn't look like a valid email")
            String email,

            WorkspaceRole role
    ) {}

    public record AcceptInvitationRequest(
            @NotBlank String token
    ) {}

    /**
     * A workspace already running on the user's email domain, offered at signup step 2 so they can find
     * their firm instead of accidentally starting a second copy of it.
     *
     * <p>Deliberately thin. This is shown to somebody who is <b>not yet a member</b>, so it carries the
     * name, who runs it and how big it is — enough to recognise your own firm — and nothing about the
     * work going on inside it.
     */
    public record JoinableWorkspace(
            UUID id,
            String name,
            String logoMark,
            int memberCount,
            String adminName
    ) {}

    public record RequestToJoinRequest(
            @NotNull(message = "Choose a workspace to join")
            UUID workspaceId,

            /** A suggestion only. The approving admin picks the real role. */
            WorkspaceRole requestedRole
    ) {}

    /** Someone waiting on an admin's decision, as the admin sees them. */
    public record PendingMemberResponse(
            UUID memberId,
            UUID userId,
            String fullName,
            String email,
            WorkspaceRole requestedRole,
            java.time.Instant requestedAt
    ) {}

    public record ApproveMemberRequest(
            /** Null keeps the role they asked for. */
            WorkspaceRole role
    ) {}
}
