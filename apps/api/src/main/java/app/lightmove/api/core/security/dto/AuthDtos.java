package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.service.PasswordPolicy;
import app.lightmove.api.workspace.dto.WorkspaceDtos.WorkspaceSummary;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The HTTP contract for authentication.
 *
 * <p>The validation messages are what the user reads under the field, so they read as instructions
 * ("Use at least 8 characters"), mirroring the frontend's Zod schema — the client validates for a fast
 * response, the server because the client can be bypassed with one curl command.
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
     * <p>The refresh token is <b>deliberately absent</b> — it leaves in an httpOnly cookie script cannot
     * read, and putting it in this body would defeat the point of the cookie.
     *
     * @param expiresIn seconds, so the SPA can schedule a refresh before the token dies.
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

            /** Null until signup step 2 — the frontend routes into the app or back into the wizard on it. */
            WorkspaceSummary workspace,

            /**
             * True when the user filled in the wizard but has not verified, so what they asked for is held
             * (see {@code PendingOnboarding}). The SPA routes on actual state, not a step counter, so a
             * closed tab does not lose the wizard.
             */
            boolean onboardingHeld,

            /**
             * True when the user asked to join a workspace and an admin has not yet decided — this, and
             * only this, is "waiting for approval". Sent rather than inferred: inferring it from a verified
             * address stranded anyone who verified before finishing the wizard.
             */
            boolean awaitingApproval
    ) {}
}
