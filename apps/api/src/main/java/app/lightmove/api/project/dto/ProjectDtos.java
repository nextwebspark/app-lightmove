package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectRole;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.workspace.constant.WorkspaceRole;
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
            int companies,
            int candidates,
            Instant createdAt
    ) {}

    /** A seat on the team. The lead is the row whose projectRole is LEAD. */
    public record TeamMemberResponse(
            UUID memberId,
            UUID userId,
            String fullName,
            WorkspaceRole workspaceRole,
            ProjectRole projectRole
    ) {}

    public record CreateProjectRequest(
            @NotNull(message = "Choose a client")
            UUID clientId,

            @NotBlank(message = "Enter the position title")
            @Size(max = 160, message = "That title is too long")
            String positionTitle,

            @NotNull(message = "Choose a lead")
            UUID leadMemberId,

            LocalDate targetDate
    ) {}

    /** Both fields optional; a null leaves the value as it is. */
    public record UpdateProjectRequest(
            UUID leadMemberId,
            LocalDate targetDate
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
