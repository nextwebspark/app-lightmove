package app.lightmove.api.project.dto;

import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The HTTP contract for projects and clients. Pipeline counts are 0 until pipeline tables exist. */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectResponse(
            UUID id,
            UUID clientId,
            String clientName,
            String positionTitle,
            ProjectStage stage,
            ProjectHealth health,
            LocalDate targetDate,
            List<TeamMemberResponse> team,
            List<AttachedRepresentativeResponse> representatives,
            int companies,
            int candidates,
            Instant createdAt
    ) {}

    /**
     * A client-side contact on this mandate: seated with a CLIENT seat (ACTIVE) or attached while
     * their portal invitation is still outstanding (INVITED — they are seated automatically on accept).
     */
    public record AttachedRepresentativeResponse(
            UUID representativeId,
            String fullName,
            String position,
            String email,
            ClientRepStatus status
    ) {}

    /**
     * A seat on the team. {@code projectRoles} stays a list although a seat holds one staff role: a
     * client representative who also staffs the mandate holds {@code [CLIENT, LEAD]}.
     */
    public record TeamMemberResponse(
            UUID memberId,
            UUID userId,
            String fullName,
            List<WorkspaceRole> workspaceRoles,
            List<ProjectRole> projectRoles
    ) {}

    /** The creator becomes the project's lead; there is no lead to choose up front. */
    public record CreateProjectRequest(
            @NotNull(message = "Choose a client")
            UUID clientId,

            @NotBlank(message = "Enter the position title")
            @Size(max = 160, message = "That title is too long")
            String positionTitle,

            LocalDate targetDate
    ) {}

    public record UpdateProjectRequest(
            LocalDate targetDate
    ) {}

    /**
     * PUT of a seat: the one staff role the member holds on this mandate afterwards. Singular by
     * contract, so the one-role-per-seat rule cannot be forgotten in a runtime check. CLIENT is not
     * seatable here — it comes from attaching a representative — and a seat that already holds it
     * keeps it.
     */
    public record PutTeamMemberRequest(
            @NotNull(message = "Choose a role")
            ProjectRole role
    ) {}

    /** Attach a client representative to this mandate as a read-only CLIENT seat. */
    public record AttachRepresentativeRequest(
            @NotNull(message = "Choose a representative")
            UUID representativeId
    ) {}
}
