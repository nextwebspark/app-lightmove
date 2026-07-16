package app.lightmove.api.project.dto;

import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
            int companies,
            int candidates,
            Instant createdAt
    ) {}

    /** A seat on the team. Both tiers' roles are sets — the creator holds {ADMIN, LEAD} from birth. */
    public record TeamMemberResponse(
            UUID memberId,
            UUID userId,
            String fullName,
            List<WorkspaceRole> workspaceRoles,
            List<ProjectRole> projectRoles
    ) {}

    /** The creator becomes the project's ADMIN (and LEAD); there is no lead to choose up front. */
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

    /** PUT of a seat: replace-set — the full set of project roles the member holds afterwards. */
    public record PutTeamMemberRequest(
            @NotEmpty(message = "Choose at least one role")
            Set<ProjectRole> roles
    ) {}

    public record ClientResponse(
            UUID id,
            String name,
            String hqCountry,
            long activeMandates,
            long deliveredMandates
    ) {}

    public record CreateClientRequest(
            @NotBlank(message = "Enter the client's name")
            @Size(max = 160, message = "That name is too long")
            String name,

            @Size(max = 64) String hqCountry
    ) {}
}
