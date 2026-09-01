package app.lightmove.api.position.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.repository.PositionRepository;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the brief behind a mandate, drafting one when there is none.
 *
 * <p>Every load goes through the project's {@code (id, workspaceId)} lookup, and the workspace id
 * comes from the principal — so a foreign mandate 404s before any brief row is touched.
 *
 * <p>Drafting happens on two paths and must behave identically on both: when a mandate is created,
 * and lazily on first read for the mandates that predate the position tables.
 */
@Component
@RequiredArgsConstructor
class PositionBriefLoader {

    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final PositionTemplateService templates;

    PositionBrief require(UUID workspaceId, UUID projectId) {
        Project project = projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Position position = positions.findByProjectId(project.getId())
                .orElseGet(() -> draft(workspaceId, project.getId(), project.getPositionTitle(),
                        hqCountryOf(project.getClientId(), workspaceId)));
        return new PositionBrief(project, position);
    }

    /**
     * The seeded brief a new mandate starts from: the template its role title matches in the
     * workspace's catalog, with the client's home country pre-filled as the location.
     *
     * <p>The location is written after the template rather than through it — a template describes a
     * kind of role and has never met this client, so where the seat sits is not its to say.
     *
     * <p>A catalog with nothing in it drafts a blank brief rather than failing: the library is
     * reference content, and a mandate must still be creatable against a database that has none.
     */
    Position draft(UUID workspaceId, UUID projectId, String positionTitle, String location) {
        Position position = Position.forProject(projectId, location);
        templates.matching(workspaceId, positionTitle)
                .ifPresent(template -> PositionTemplateApplier.applyTo(position, template));
        return positions.save(position);
    }

    private String hqCountryOf(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .map(Client::getHqCountry)
                .orElse(null);
    }
}
