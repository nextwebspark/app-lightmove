package app.lightmove.api.workspace.dto;

import app.lightmove.api.core.security.rbac.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The HTTP contract for workspace onboarding and membership.
 *
 * <p>Split from the auth contract so a feature owns its own payloads; {@code AuthDtos.UserResponse}
 * still embeds {@link WorkspaceSummary}, which is the one place the auth response reaches into workspace.
 */
public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    public record WorkspaceSummary(
            UUID id,
            String name,
            String slug,
            String logoMark,
            String emailDomain,

            /** The caller's workspace roles — a set, sorted for stable rendering. */
            List<WorkspaceRole> roles
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

    /** One row of the active roster. */
    public record MemberResponse(
            UUID memberId,
            UUID userId,
            String fullName,
            String email,
            String title,
            List<WorkspaceRole> roles,
            Instant joinedAt
    ) {}

    /** Replace-set: the full set of roles the member holds afterwards. */
    public record ChangeRolesRequest(
            @NotEmpty(message = "Choose at least one role")
            Set<WorkspaceRole> roles
    ) {}

    /** Settings → General. */
    public record WorkspaceResponse(
            UUID id,
            String name,
            String slug,
            String logoMark,
            String emailDomain,
            String defaultRegion,
            String defaultCurrency,
            String plan,
            long memberCount,
            Instant createdAt
    ) {}

    public record UpdateWorkspaceSettingsRequest(
            @NotBlank(message = "Enter the workspace name")
            @Size(max = 160, message = "That name is too long")
            String name,

            String defaultRegion,
            String defaultCurrency
    ) {}

    /** Deletion demands the workspace name typed back; the server verifies it too. */
    public record DeleteWorkspaceRequest(
            @NotBlank(message = "Type the workspace name to confirm")
            String confirmName
    ) {}

    /** An outstanding invitation, as the Settings → Members screen lists them. */
    public record InvitationResponse(
            UUID id,
            String email,
            WorkspaceRole role,
            String invitedByName,
            Instant createdAt,
            Instant expiresAt
    ) {}
}
